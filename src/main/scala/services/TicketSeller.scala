package services

import akka.actor.{Actor, Props}
import akka.cluster.sharding.ShardRegion
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import domain.{Ticket, _}

import scala.reflect.{ClassTag, classTag}

object TicketSeller {
  val Name = "ticket-seller"

  def props(): Props = {
    Props(new TicketSeller())
  }

  // States
  sealed trait State extends FSMState

  case object Idle extends State {
    override def identifier: String = "idle"
  }

  case object Active extends State {
    override def identifier: String = "active"
  }

  case object SoldOut extends State {
    override def identifier: String = "sold-out"
  }

  // Data
  sealed trait BoxOffice {
    def buy(): (Option[Ticket], BoxOffice)
    def tickets(): Seq[Ticket]
  }

  case object EmptyBoxOffice extends BoxOffice {
    def buy(): (Option[Ticket], BoxOffice) = (None, EmptyBoxOffice)
    def tickets(): Seq[Ticket] = Seq.empty
  }

  case class NonEmptyBoxOffice(tickets: Seq[Ticket]) extends BoxOffice {
    def buy(): (Option[Ticket], BoxOffice) = {
      tickets.toList match {
        case ticket :: Nil => (Some(ticket), EmptyBoxOffice)
        case ticket :: rest => (Some(ticket), NonEmptyBoxOffice(rest))
        case Nil => (None, EmptyBoxOffice)
      }
    }
  }

  // Events
  sealed trait DomainEvent
  case class TicketsAdded(tickets: Seq[Ticket]) extends DomainEvent
  case object TicketBought extends DomainEvent

  object Sharding {
    val extractEntityId: ShardRegion.ExtractEntityId = {
      case msg @ EventMessage(jobId, _) => (jobId.toString, msg)
    }

    val extractShardId: ShardRegion.ExtractShardId = {
      case EventMessage(jobId, _) => (math.abs(jobId.toString.hashCode) % 100).toString
    }
  }

}

class TicketSeller extends Actor with PersistentFSM[TicketSeller.State, TicketSeller.BoxOffice, TicketSeller.DomainEvent] {

  import TicketSeller._

  log.info("Starting TicketSeller at {}", self.path)

  override def persistenceId: String = {
    // Note:
    // self.path.parent.parent.name is the ShardRegion actor name: ticket-seller
    // self.path.parent.name is the Shard supervisor actor name: 5
    // self.path.name is the sharded Entity actor name: 597be7e24e00004500292035
    s"${self.path.parent.parent.name}-${self.path.parent.name}-${self.path.name}"
  }

  override def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]

  override def applyEvent(domainEvent: DomainEvent, boxOfficeBeforeEvent: BoxOffice): BoxOffice = {
    domainEvent match {
      case TicketsAdded(tickets) => NonEmptyBoxOffice(tickets)

      case TicketBought =>
        val (_, newBoxOffice) = boxOfficeBeforeEvent.buy()
        newBoxOffice
    }
  }

  startWith(Idle, EmptyBoxOffice)

  when(Idle) {
    case Event(EventMessage(_, AddTickets(tickets)), _) => {
      goto(Active) applying TicketsAdded(tickets)
    }
  }

  when(Active) {
    case Event(EventMessage(_, BuyTicket), boxOffice: BoxOffice) => {
      val (boughtTicket, newBoxOffice) = boxOffice.buy()
      newBoxOffice match {
        case _: NonEmptyBoxOffice => stay applying TicketBought andThen { _ =>
          sender ! boughtTicket // respond to the sender once the event has been persisted successfully
        }
        case EmptyBoxOffice => goto(SoldOut) applying TicketBought andThen { _ =>
          sender ! boughtTicket // respond to the sender once the event has been persisted successfully
        }
      }
    }
  }

  when(SoldOut) {
    case Event(EventMessage(_, BuyTicket), boxOffice: BoxOffice) => {
      val (boughtTicket, _) = boxOffice.buy()
      sender ! boughtTicket
      stay
    }
  }

  whenUnhandled {
    // common code for all states
    case Event(EventMessage(_, ListTickets), boxOffice: BoxOffice) => {
      sender ! boxOffice.tickets
      stay
    }

    case Event(EventMessage(name, Cancel), _) => {
      log.info("Cancelling Ticket Seller for Event {}", name)
      stop
    }

    case Event(e, s) => {
      log.warning("Received unhandled msg {} in state {}/{}", e, stateName, s)
      stay
    }
  }

}
