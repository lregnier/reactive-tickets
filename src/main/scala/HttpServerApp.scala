import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import api.EventHttpEndpoint
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
  // Initiates singleton TicketSellerSupervisor in the Cluster
  val ticketSellerSupervisorSingleton =
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = TicketSellerSupervisor.props(),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withSingletonName(TicketSellerSupervisor.Name)),
      name = s"${TicketSellerSupervisor.Name}-singleton")

  // Initiates proxy for singleton
  val ticketSellerSupervisorSingletonProxy =
    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = ticketSellerSupervisorSingleton.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(system).withSingletonName(TicketSellerSupervisor.Name)),
      name = s"${TicketSellerSupervisor.Name}-proxy")

  val eventManager = system.actorOf(EventManager.props(eventRepository, ticketSellerSupervisorSingletonProxy), EventManager.Name)
}

trait EndpointsModule { self: AkkaModule with ServicesModule =>
  import Directives._

  val routes =
    pathPrefix("api") {
      EventHttpEndpoint(eventManager).routes
    }
}


object HttpServerApp extends App with AkkaModule with SettingsModule with PersistenceModule with ServicesModule with EndpointsModule {
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