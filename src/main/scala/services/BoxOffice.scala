package services

import akka.actor._
import domain.{Show, Ticket, _}

object BoxOffice {
  val Name = "box-office"

  def props(ticketSellerSupervisor: ActorRef): Props = {
    Props(new BoxOffice(ticketSellerSupervisor))
  }

  sealed trait ShowCreationResponse
  case class ShowCreated(event: Show) extends ShowCreationResponse
  case object ShowExists extends ShowCreationResponse

  case object NonExistentShow
}

class BoxOffice(ticketSellerSupervisor: ActorRef) extends Actor {
  import BoxOffice._

  implicit val ec = context.dispatcher

  var shows = Set.empty[Show]

  def receive = {
    case EventMessage(name, CreateShow(ticketsNmbr)) => {
      def create(): Show = {
        val show = Show(name)
        shows += show
        show
      }

      def addTickets() = {
        val newTickets = (1 to ticketsNmbr).map(_ => Ticket()).toSet
        ticketSellerSupervisor ! EventMessage(name, AddTickets(newTickets))
      }

      val result: ShowCreationResponse =
        if (eventExists(name)) {
          ShowExists
        }
        else {
          val show = create()
          addTickets
          ShowCreated(show)
        }

      sender ! result

    }

    case EventMessage(name, RetrieveShow) => {
      def retrieveShow(): Option[Show] = {
        shows.find(_.name == name)
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

    case ListShows => {
      def listShows(): Set[Show] = {
        shows
      }
      sender ! listShows()
    }

    case msg @ EventMessage(name, Cancel) =>

      def remove(name: String): Option[Show] = {
        val result = shows.find(_.name == name)
        result.foreach(s => shows.filterNot(_ == s))
        result
      }

      sender ! remove(name)
  }

  def eventExists(name: String): Boolean = {
    shows.exists(_.name == name)
  }

  def verifyEventAndThen(name: String)(func: String => Unit): Unit = {
    if (eventExists(name)) func(name)
    else sender ! None
  }
}

