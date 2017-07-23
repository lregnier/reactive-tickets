package services

import akka.actor.{Actor, FSM, Props}
import domain.Ticket
import domain._

object TicketSeller {
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
  case class TicketsAvailable(tickets: Set[Ticket]) extends Data

}

class TicketSeller extends Actor with FSM[TicketSeller.State, TicketSeller.Data] {

  import TicketSeller._

  startWith(Idle, EmptyData)

  when(Idle) {
    case Event(EventMessage(_, AddTickets(newTickets)), _) => {
      log.info("Adding new tickets {}", newTickets)
      goto(Active) using TicketsAvailable(newTickets)
    }
  }

  when(Active) {
    case Event(EventMessage(_, BuyTicket), TicketsAvailable(tickets)) => {
      log.info("Selling a ticket")
      val ticket = tickets.last
      sender ! Some(ticket)
      val currentTickets = tickets.dropRight(1)
      if (currentTickets.nonEmpty) stay using TicketsAvailable(currentTickets)
      else goto(SoldOut) using TicketsAvailable(currentTickets)
    }

    case Event(EventMessage(_, ListTickets), TicketsAvailable(tickets)) => {
      sender ! tickets
      stay
    }
  }

  when(SoldOut) {
    case Event(EventMessage(_, BuyTicket), _) => {
      sender ! None
      stay
    }

    case Event(EventMessage(_, ListTickets), _) => {
      sender ! Set.empty
      stay
    }
  }

  whenUnhandled {
    case Event(EventMessage(_, Cancel), currentState) => {
      sender ! currentState
      stop
    }

    case Event(e, s) => {
      log.warning("Received unhandled msg {} in state {}/{}", e, stateName, s)
      stay
    }
  }

  initialize()

}



