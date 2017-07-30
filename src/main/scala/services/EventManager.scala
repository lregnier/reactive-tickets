package services

import java.util.UUID

import akka.actor._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import domain.{Event, _}
import persistence.EventRepository

import scala.concurrent.Future
import scala.concurrent.duration._

object EventManager {
  val Name = "event-manager"

  def props(eventRepository: EventRepository, ticketSellerSupervisor: ActorRef): Props = {
    Props(new EventManager(eventRepository: EventRepository, ticketSellerSupervisor))
  }

}

class EventManager(eventRepository: EventRepository, ticketSellerSupervisor: ActorRef) extends Actor {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive = {
    case CreateEvent(name, description, ticketsNumber) => {
      val result = eventRepository.create(name, description)

      // Create TicketSeller as side-effect
      result onSuccess {
        case event: Event => ticketSellerSupervisor ! EventMessage(event.id, AddTickets(TicketsGenerator.generate(ticketsNumber)))
      }

      result pipeTo sender
    }

    case EventMessage(id, RetrieveEvent) => {
      val result = eventRepository.retrieve(id)

      result pipeTo sender
    }

    case ListEvents => {
      val result = eventRepository.list()

      result pipeTo sender
    }

    case msg @ EventMessage(id, BuyTicket) => {
      def buyTicket(): Future[Option[Ticket]] = {
        (ticketSellerSupervisor ? msg).mapTo[Option[Ticket]]
      }

      val result =
        for {
          _ <- verifyEvent(id)
          ticket <- buyTicket()
        } yield ticket

      result pipeTo sender
    }

    case msg @ EventMessage(id, ListTickets) => {
      def retrieveTickets(): Future[Seq[Ticket]] = {
        (ticketSellerSupervisor ? msg).mapTo[Seq[Ticket]]
      }

      val result =
        for {
          _ <- verifyEvent(id)
          tickets <- retrieveTickets()
        } yield tickets

      result pipeTo sender
    }

    case msg @ EventMessage(id, Cancel) => {
      val result = eventRepository.remove(id)

      // Remove TicketSeller as side-effect
      result onSuccess {
        case Some(_) => ticketSellerSupervisor ! msg
      }

      result pipeTo sender
    }

    case CancelAll => {
      val result =
        for {
          events <- eventRepository.list()
          _ <- eventRepository.removeAll()
        } yield events.map(_.id)

      // Remove TicketSellers as side-effect
      result onSuccess {
        case eventIds =>
          eventIds foreach { id =>
            ticketSellerSupervisor ! EventMessage(id, Cancel)
          }
      }

      result pipeTo sender
    }

  }

  def verifyEvent(id: String): Future[Event] = {
    eventRepository.retrieve(id) flatMap {
      case Some(event) => Future.successful(event)
      case None => Future.failed(EntityNotFoundException(s"Non-existent event for id: $id"))
    }
  }

}

object TicketsGenerator {
  def generate(ticketsNumber: Int): Seq[Ticket] = {
    (1 to ticketsNumber).map(_ => Ticket(UUID.randomUUID().toString))
  }
}

case class EntityNotFoundException(msg: String) extends Exception
