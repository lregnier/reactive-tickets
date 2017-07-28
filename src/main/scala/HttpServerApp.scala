import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import api.{CustomExceptionHandling, EventHttpEndpoint}
import com.typesafe.config.{Config, ConfigFactory}
import persistence.EventRepository
import reactivemongo.api.{MongoConnection, MongoDriver}
import services.{EventManager, TicketSellerSupervisor}

import scala.concurrent.Await
import scala.concurrent.duration._

trait AkkaModule {
  implicit val system = ActorSystem("reactive-tickets-system")
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
}

trait SettingsModule {
  private val config = ConfigFactory.load()
  val mongoDbSettings = new MongoDbSettings(config)
  val httpSettings = new HttpSettings(config)

  class MongoDbSettings(config: Config) {
    private val mongoDbConfig = config.getConfig("mongodb")
    val uri = mongoDbConfig.getString("uri")
  }

  class HttpSettings(config: Config) {
    private val httpConfig = config.getConfig("http")
    val host = httpConfig.getString("host")
    val port = httpConfig.getInt("port")
  }
}

trait PersistenceModule { self: AkkaModule with SettingsModule =>
  private val driver = MongoDriver()
  private val parsedUri = MongoConnection.parseURI(mongoDbSettings.uri)
  private val connection = parsedUri.map(driver.connection(_)).get // Fail fast here

  val eventRepository = new EventRepository(connection)
}

trait ServicesModule { self: AkkaModule with PersistenceModule =>
  val ticketSellerSupervisor = system.actorOf(TicketSellerSupervisor.props(), TicketSellerSupervisor.Name)
  val eventManager = system.actorOf(EventManager.props(eventRepository ,ticketSellerSupervisor), EventManager.Name)
}

trait EndpointsModule { self: AkkaModule with ServicesModule =>
  import Directives._

  val routes =
    pathPrefix("api") {
      EventHttpEndpoint(eventManager).routes
    }
}


object HttpServerApp extends App with AkkaModule with SettingsModule with PersistenceModule with ServicesModule with EndpointsModule {
  import CustomExceptionHandling._

  // Initialize server
  Http().bindAndHandle(routes, httpSettings.host, httpSettings.port)

  // Add system hooks
  scala.sys.addShutdownHook {
    val log = Logging(system, getClass)
    log.info("Shutting down server and actor system")
    system.terminate()
    Await.result(system.whenTerminated, 30 seconds)
  }

}