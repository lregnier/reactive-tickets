package services

import akka.actor.{Actor, FSM, Props}
import akka.cluster.sharding.ShardRegion
import domain.{Ticket, _}

object TicketSeller {
  val Name = "ticket-seller"

  def props(): Props = {
    Props(new TicketSeller())
  }

  // States
  sealed trait State
  case object Idle extends State
  case object Active extends State
  case object SoldOut extends State

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

  object Sharding {
    val extractEntityId: ShardRegion.ExtractEntityId = {
      case msg @ EventMessage(jobId, _) => (jobId.toString, msg)
    }

    val extractShardId: ShardRegion.ExtractShardId = {
      case EventMessage(jobId, _) => (math.abs(jobId.toString.hashCode) % 100).toString
    }
  }

}

class TicketSeller extends Actor with FSM[TicketSeller.State, TicketSeller.BoxOffice] {

  import TicketSeller._

  log.info("Starting TicketSeller at {}", self.path)

  startWith(Idle, EmptyBoxOffice)

  when(Idle) {
    case Event(EventMessage(_, AddTickets(tickets)), _) => {
      goto(Active) using NonEmptyBoxOffice(tickets)
    }
  }

  when(Active) {
    case Event(EventMessage(_, BuyTicket), boxOffice: BoxOffice) => {
      val (boughtTicket, newBoxOffice) = boxOffice.buy()
      sender ! boughtTicket
      newBoxOffice match {
        case bo: NonEmptyBoxOffice => stay using bo
        case EmptyBoxOffice => goto(SoldOut) using EmptyBoxOffice
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

  initialize()

}
