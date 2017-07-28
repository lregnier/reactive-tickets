package api

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import domain.{Event, EventMessage, _}
import org.json4s.ext.UUIDSerializer
import org.json4s.{DefaultFormats, jackson}

import scala.concurrent.duration._

trait Json4sJacksonSupport extends Json4sSupport { self: Directives =>
  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats + UUIDSerializer
}

object EventHttpEndpoint {
  def apply(boxOfficeService: ActorRef): EventHttpEndpoint = {
    new EventHttpEndpoint(boxOfficeService)
  }

  case class CreateEventRepresentation(name: String, ticketsNumber: Int)
}

class EventHttpEndpoint(boxOfficeService: ActorRef) extends Directives with Json4sJacksonSupport {

  import EventHttpEndpoint._
  implicit val timeout = Timeout(10 seconds)

  def create =
    (pathEnd & post & entity(as[CreateEventRepresentation])) { createEvent =>
      extractUri { uri =>
        val msg = CreateEvent(createEvent.name, createEvent.ticketsNumber)
        onSuccess((boxOfficeService ? msg).mapTo[Event]) { event =>
          respondWithHeader(Location(s"$uri/${event.id}")) {
            complete(StatusCodes.Created, event)
          }
        }
      }
    }

  def retrieve =
    (path(Segment) & get) { eventId =>
      onSuccess((boxOfficeService ? EventMessage(eventId, RetrieveEvent)).mapTo[Option[Event]]) {
        case Some(task) => complete(task)
        case None => complete(StatusCodes.NotFound, s"Non-existent event for id: $eventId")
      }
    }

  def remove =
    (path(Segment) & delete) { eventId =>
      onSuccess((boxOfficeService ? EventMessage(eventId, Cancel)).mapTo[Option[String]]) {
        case Some(_) => complete(StatusCodes.NoContent)
        case None => complete(StatusCodes.NotFound, s"Non-existent event for id: $eventId")
      }
    }

  def list =
    (pathEnd & get) {
      onSuccess((boxOfficeService ? ListEvents).mapTo[Seq[Event]]) { events =>
        complete(events)
      }
    }

  def buyTicket =
    (path(Segment / "tickets" / "purchase") & post) { eventId =>
      onSuccess((boxOfficeService ? EventMessage(eventId, BuyTicket)).mapTo[Option[Ticket]]) {
        case Some(ticket) => complete(ticket)
        case None => complete(StatusCodes.NotFound, s"No tickets available for event id: $eventId")
      }
    }

  def listTickets =
    (path(Segment / "tickets") & get) { eventId =>
      onSuccess((boxOfficeService ? EventMessage(eventId, ListTickets)).mapTo[Seq[Ticket]]) { events =>
        complete(events)
      }
    }

  def routes =
    pathPrefix("events") {
      create ~
      retrieve ~
      remove ~
      list ~
      buyTicket ~
      listTickets
    }

}


