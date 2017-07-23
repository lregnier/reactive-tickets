import akka.actor.{ActorSystem, PoisonPill}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import api.EventHttpEndpoint
import com.typesafe.config.{Config, ConfigFactory}
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
  val httpSettings = new HttpSettings(config)

  class HttpSettings(config: Config) {
    private val httpConfig = config.getConfig("http")

    val host = httpConfig.getString("host")
    val port = httpConfig.getInt("port")
  }
}

trait ServicesModule { self: AkkaModule =>
  // Initiates singleton TicketSellerSupervisor in the Cluster
  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = TicketSellerSupervisor.props(),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system).withSingletonName(TicketSellerSupervisor.Name)),
    name = s"${TicketSellerSupervisor.Name}-singleton")

  // Initiates proxy for singleton
  val ticketSellerSupervisor =
  system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/consumer",
      settings = ClusterSingletonProxySettings(system)),
    name = s"${TicketSellerSupervisor.Name}-proxy")

  val boxOffice = system.actorOf(EventManager.props(ticketSellerSupervisor), EventManager.Name)
}

trait EndpointsModule { self: AkkaModule with ServicesModule =>
  import Directives._

  val routes =
    pathPrefix("api") {
      EventHttpEndpoint(boxOffice).routes
    }
}


object HttpServerApp extends App with AkkaModule with SettingsModule with ServicesModule with EndpointsModule {
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