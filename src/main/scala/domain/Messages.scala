package domain

trait EventMessagePayload
case class EventMessage(name: String, payload: EventMessagePayload)

case class CreateEvent(ticketsNumber: Int) extends EventMessagePayload
case object RetrieveEvent extends EventMessagePayload
case class AddTickets(tickets: Seq[Ticket]) extends EventMessagePayload
case object ListTickets extends EventMessagePayload
case object BuyTicket extends EventMessagePayload
case object Cancel extends EventMessagePayload

case object ListEvents