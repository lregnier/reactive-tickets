package domain

import java.util.UUID

case class Show(name: String)

case class Ticket(id: UUID = UUID.randomUUID())
