package domain

trait EventMessagePayload
case class EventMessage(name: String, payload: EventMessagePayload)

case class CreateShow(ticketsNmbr: Int) extends EventMessagePayload
case object RetrieveShow extends EventMessagePayload
case class AddTickets(tickets: Set[Ticket]) extends EventMessagePayload
case object ListTickets extends EventMessagePayload
case object BuyTicket extends EventMessagePayload
case object Cancel extends EventMessagePayload

case object ListShows
