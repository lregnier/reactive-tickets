package domain

case class EventMessage(eventId: String, payload: EventMessagePayload)
trait EventMessagePayload

case object RetrieveEvent extends EventMessagePayload
case class AddTickets(tickets: Seq[Ticket]) extends EventMessagePayload
case object ListTickets extends EventMessagePayload
case object BuyTicket extends EventMessagePayload
case object Cancel extends EventMessagePayload

case class CreateEvent(name: String, description: String, ticketsNumber: Int)
case object ListEvents
case object CancelAll
