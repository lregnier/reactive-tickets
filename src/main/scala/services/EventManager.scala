package services

import akka.actor._
import domain.{Event, _}

import scala.util.Try

object EventManager {
  val Name = "event-manager"

  def props(ticketSellerSupervisor: ActorRef): Props = {
    Props(new EventManager(ticketSellerSupervisor))
  }

}

class EventManager(ticketSellerSupervisor: ActorRef) extends Actor {

  implicit val ec = context.dispatcher

  var events = Set.empty[Event]

  def receive = {
    case EventMessage(name, CreateEvent(ticketsNumber)) => {
      def create(): Try[Event] = Try {
        if (eventExists(name)) throw new IllegalArgumentException(s"An event with name '$name' already exists.")
        else {
          val show = Event(name)
          events += show
          show
        }
      }

      val result =
        create().map { show =>
          ticketSellerSupervisor ! EventMessage(name, AddTickets(ticketsNumber)) // TODO: This should be a side-effect
          show
        }

      sender ! result

    }

    case EventMessage(name, RetrieveEvent) => {
      def retrieveShow(): Option[Event] = {
        events.find(_.name == name)
      }

      sender ! retrieveShow()
    }

    case msg @ EventMessage(name, BuyTicket) => {
      verifyEventAndThen(name) { _ =>
        ticketSellerSupervisor forward msg
      }
    }

    case msg @ EventMessage(name, ListTickets) => {
      verifyEventAndThen(name) { _ =>
        ticketSellerSupervisor forward msg
      }
    }

    case ListEvents => {
      def listShows(): Seq[Event] = {
        events.toSeq
      }

      sender ! listShows()
    }

    case msg @ EventMessage(name, Cancel) =>
      def remove(): Option[Event] = {
        val result = events.find(_.name == name)
        result.foreach(s => events.filterNot(_ == s))
        result
      }

      val result =
        remove().map { show =>
          ticketSellerSupervisor ! msg // TODO: This should be a side-effect
          show
        }

      sender ! result
  }

  def eventExists(name: String): Boolean = {
    events.exists(_.name == name)
  }

  def verifyEventAndThen(name: String)(func: String => Unit): Unit = {
    if (eventExists(name)) func(name)
    else sender ! None
  }
}

