package persistence

import domain.Event
import reactivemongo.api.{DefaultDB, MongoConnection}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONObjectID, Macros}

import scala.concurrent.{ExecutionContext, Future}

object EventRepository {
  implicit val eventReader: BSONDocumentReader[Event] = Macros.reader[Event]
}

class EventRepository(connection: MongoConnection) {

  import EventRepository._

  private def db(implicit ec: ExecutionContext): Future[DefaultDB] = connection.database("reactive-tickets")
  private def collection(implicit ec: ExecutionContext): Future[BSONCollection] = db.map(_.collection("event"))

  def create(name: String, description: String)(implicit ec: ExecutionContext): Future[Event] = {
    val _id = BSONObjectID.generate
    val id = _id.stringify
    val document = BSONDocument(
      "_id" -> _id,
      "id" -> id,
      "name" -> name,
      "description" -> description)

    collection.flatMap(_.insert(document).map(_ => Event(id, description, name)))
  }


  def retrieve(id: String)(implicit ec: ExecutionContext): Future[Option[Event]] = {
    def retrieve(_id: BSONObjectID): Future[Option[Event]] = {
      val query = BSONDocument("_id" -> _id)
      collection.flatMap(_.find(query).one[Event])
    }

    val result =
      for {
        _id <- parseId(id)
        event <- retrieve(_id)
      } yield event
    result
  }

  def remove(id: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    def remove(_id: BSONObjectID): Future[Option[String]] = {
      val query = BSONDocument("_id" -> _id)
      collection.flatMap(_.remove(query).map {
        case result if result.n > 0 => Some(id)
        case _ => None
      })
    }

    val result =
      for {
        _id <- parseId(id)
        event <- remove(_id)
      } yield event
    result
  }

  def list()(implicit ec: ExecutionContext): Future[Seq[Event]] = {
     collection.flatMap(_.find(BSONDocument()).cursor[Event]().collect[List]())
  }

  private def parseId(id: String): Future[BSONObjectID] = {
    Future.fromTry(BSONObjectID.parse(id))
  }

}