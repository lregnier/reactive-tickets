package api

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import domain.{EventMessage, Show, _}
import org.json4s.{DefaultFormats, jackson}
import scala.concurrent.duration._

trait Json4sJacksonSupport extends Json4sSupport { self: Directives =>
  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats
}

object BoxOfficeEndpoint {
  def apply(boxOfficeService: ActorRef): BoxOfficeEndpoint = {
    new BoxOfficeEndpoint(boxOfficeService)
  }

  case class CreateEventRepresentation(name: String, ticketsNmbr: Int)
}

class BoxOfficeEndpoint(boxOfficeService: ActorRef) extends Directives with Json4sJacksonSupport {

  import BoxOfficeEndpoint._
  implicit val timeout = Timeout(10 seconds)

  def create =
    (pathEnd & post & entity(as[CreateEventRepresentation])) { createEvent =>
      extractUri { uri =>
        val msg = EventMessage(createEvent.name, CreateShow(createEvent.ticketsNmbr))
        onSuccess((boxOfficeService ? msg).mapTo[Show]) { show =>
          respondWithHeader(Location(s"$uri/${show.name}")) {
            complete(StatusCodes.Created, show)
          }
        }
      }
    }

  def retrieve =
    (path(Segment) & get) { name =>
      onSuccess((boxOfficeService ? EventMessage(name, RetrieveShow)).mapTo[Option[Show]]) {
        case Some(task) => complete(task)
        case None => complete(StatusCodes.NotFound)
      }
    }

  def remove =
    (path(Segment) & delete) { name =>
      onSuccess((boxOfficeService ? EventMessage(name, Cancel)).mapTo[Option[Show]]) {
        case Some(_) => complete(StatusCodes.NoContent)
        case None => complete(StatusCodes.NotFound)
      }
    }

  def list =
    (pathEnd & get) {
      onSuccess((boxOfficeService ? ListShows).mapTo[Seq[Show]]) { shows =>
        complete(shows)
      }
    }

  def routes =
  pathPrefix("event"){
    create ~
    retrieve ~
    remove ~
    list
  }

}


