package footbalisto

import footbalisto.domain.{Model, Ranking}
import reactivemongo.api.MongoConnection.ParsedURI
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{DefaultDB, MongoConnection, MongoDriver}
import reactivemongo.bson.{BSONDocumentReader, BSONDocumentWriter, Macros, document}

import scala.concurrent.Future
import scala.util.Try

object Database {

  import scala.concurrent.ExecutionContext.Implicits.global
  // use any appropriate context

  val mongoUri = "mongodb://localhost:27017/mydb?authMode=scram-sha1"


  // Connect to the database: Must be done only once per application
  val driver = MongoDriver()
  val parsedUri: Try[ParsedURI] = MongoConnection.parseURI(mongoUri)
  val connection: Try[MongoConnection] = parsedUri.map(driver.connection)

  // Database and collections: Get references
  val futureConnection: Future[MongoConnection] = Future.fromTry(connection)

  def db1: Future[DefaultDB] = futureConnection.flatMap(_.database("firstdb"))

  def personCollection: Future[BSONCollection] = db1.map(_.collection("person"))

  // Write Documents: insert or update

  implicit def personWriter: BSONDocumentWriter[Person] = Macros.writer[Person]

  // or provide a custom one

  def createPerson(person: Person): Future[Unit] = {
    personCollection.flatMap(_.insert(person).map(_ => {})) // use personWriter
  }

  def updatePerson(person: Person): Future[Int] = {
    val selector = document(
      "firstName" -> person.firstName,
      "lastName" -> person.lastName
    )

    // Update the matching person
    personCollection.flatMap(_.update(selector, person).map(_.n))
  }

  def deleteAllPersons = {
    personCollection.flatMap(_.drop(false))
  }

  implicit def personReader: BSONDocumentReader[Person] = Macros.reader[Person]

  // or provide a custom one

  def findPersonByAge(age: Int): Future[List[Person]] =
    personCollection.flatMap(_.find(document("age" -> age)). // query builder
      cursor[Person]().collect[List]())

  // collect using the result cursor
  // ... deserializes the document using personReader

  // Custom persistent types
  case class Person(firstName: String, lastName: String, age: Int)

  def insert[T <: Model](entity: T)(implicit writer: BSONDocumentWriter[T]) = {
    val collection: Future[BSONCollection] = db1.map(_.collection(entity.collection))
    collection.flatMap(_.insert(entity).map(_ => {}))
  }

}
