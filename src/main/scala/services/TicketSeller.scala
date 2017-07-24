package services

import akka.actor.{Actor, FSM, Props}
import domain.{Ticket, _}

import scala.collection.{immutable, mutable}

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
  sealed trait Data
  case object EmptyData extends Data

  class BoxOffice(addTickets: immutable.Set[Ticket]) extends Data {
    private val internalTickets = mutable.Set(addTickets.toSeq:_*)

    def buy(): Option[Ticket] = {
      val ticket = internalTickets.headOption
      ticket.foreach(t => internalTickets.remove(t))
      ticket
    }

    def tickets(): Seq[Ticket] = internalTickets.toSeq
  }

}

class TicketSeller extends Actor with FSM[TicketSeller.State, TicketSeller.Data] {

  import TicketSeller._

  startWith(Idle, EmptyData)

  when(Idle) {
    case Event(EventMessage(_, AddTickets(tickets)), _) => {
      goto(Active) using new BoxOffice(tickets)
    }
  }

  when(Active) {
    case Event(EventMessage(_, BuyTicket), boxOffice: TicketSeller.BoxOffice) => {
      sender ! boxOffice.buy()
      if (boxOffice.tickets.nonEmpty) stay using boxOffice
      else goto(SoldOut) using boxOffice
    }

    case Event(EventMessage(_, ListTickets), boxOffice: TicketSeller.BoxOffice) => {
      sender ! boxOffice.tickets
      stay
    }
  }

  when(SoldOut) {
    case Event(EventMessage(_, BuyTicket), boxOffice: TicketSeller.BoxOffice) => {
      sender ! boxOffice.buy()
      stay
    }

    case Event(EventMessage(_, ListTickets), boxOffice: TicketSeller.BoxOffice) => {
      sender ! boxOffice.tickets
      stay
    }
  }

  whenUnhandled {
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
