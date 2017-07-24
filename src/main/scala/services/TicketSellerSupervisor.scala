package services

import akka.actor.{Actor, ActorRef, Props}
import domain.EventMessage

object TicketSellerSupervisor {
  val Name = "ticket-seller-supervisor"

  def props(): Props = {
    Props(new TicketSellerSupervisor())
  }
}

class TicketSellerSupervisor extends Actor {

  def ticketSeller(name: String): ActorRef = {
    // Retrieves ticket seller for the given name, if none creates one
    val actorName = s"${TicketSeller.Name}-$name"
    context.child(actorName).getOrElse(context.actorOf(TicketSeller.props(), actorName))
  }

  def receive = {
    // Forwards message to proper ticket seller
    case msg @ EventMessage(name, _) => ticketSeller(name) forward msg
  }

}
