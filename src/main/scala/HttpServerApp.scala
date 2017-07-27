import akka.actor.ActorSystem
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import api.EventHttpEndpoint
import com.typesafe.config.{Config, ConfigFactory}
import services.{EventManager, TicketSeller}
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

trait ServicesModule { self: AkkaModule with SettingsModule =>
  // Initiates ShardRegion for TicketSeller
  val ticketSellerShardRegion =
    ClusterSharding(system).start(
      typeName = TicketSeller.Name,
      entityProps = TicketSeller.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = TicketSeller.Sharding.extractEntityId,
      extractShardId = TicketSeller.Sharding.extractShardId)

  val eventManager = system.actorOf(EventManager.props(eventRepository, ticketSellerShardRegion), EventManager.Name)
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