package footbalisto

import footbalisto.domain.Model
import reactivemongo.api.MongoConnection.ParsedURI
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DefaultDB, MongoConnection, MongoDriver}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, document}

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.Try

object MongoDao {
  import scala.concurrent.ExecutionContext.Implicits.global

  import com.typesafe.config.Config
  import com.typesafe.config.ConfigFactory

  val conf: Config = ConfigFactory.load
  val mongoUri: String = conf.getString("mongodb.connection_uri")
  val driver = MongoDriver()
  val parsedUri: Try[ParsedURI] = MongoConnection.parseURI(mongoUri)
  val connection: Try[MongoConnection] = parsedUri.map(driver.connection)
  val futureConnection: Future[MongoConnection] = Future.fromTry(connection)

  def db1: Future[DefaultDB] = futureConnection.flatMap(_.database("footbalisto"))

  def findAll[T <: Model]()(implicit reader: BSONDocumentReader[T], tag: ClassTag[T]): Future[Seq[T]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val collection: Future[BSONCollection] = db1.map(_.collection(tag.runtimeClass.getSimpleName))
    collection.flatMap(_.find(document()).cursor[T]().collect(-1, Cursor.ContOnError[Seq[T]]()))
  }

  def find[T <: Model](query: BSONDocument)(implicit reader: BSONDocumentReader[T], tag: ClassTag[T]): Future[Seq[T]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val collection: Future[BSONCollection] = db1.map(_.collection(tag.runtimeClass.getSimpleName))
    collection.flatMap(_.find(query).cursor[T]().collect(-1, Cursor.ContOnError[Seq[T]]()))
  }


  def deleteAll(collectionName: String): Future[Boolean] = {
    val collection: Future[BSONCollection] = db1.map(_.collection(collectionName))
    collection.flatMap(_.drop(failIfNotFound = false))
  }

  def insert[T <: Model](entity: T)(implicit writer: BSONDocumentWriter[T]): Future[Unit] = {
    val collection: Future[BSONCollection] = db1.map(_.collection(entity.collection))
    collection.flatMap(_.insert(entity).map(_ => {}))
  }

  def upsert[S, T <: Model](selector: S, entity: T)(implicit writer: BSONDocumentWriter[T], writer2: BSONDocumentWriter[S]): Future[Unit] = {
    //    println(s"upserting to ${entity.collection}")
    val collection: Future[BSONCollection] = db1.map(_.collection(entity.collection))
    collection.flatMap(_.update(selector, entity, upsert = true).map(_ => {}))
  }

  def ensureIndex[T](indexes: Seq[(String, IndexType)])(implicit classTag: ClassTag[T]): Future[Unit] = {
    //["season", "region", "division", "matchDay", "home", "away", "regNumberHome", "regNumberAway"]
    val collection: Future[BSONCollection] = db1.map(_.collection(classTag.runtimeClass.getSimpleName))
    collection.flatMap(_.indexesManager.ensure(Index(indexes))).map(a => {
      println(s"ensured index for ${classTag.runtimeClass.getSimpleName}: $a")
    })
  }

  def distinct[T <: Model](fieldName: String, query: Option[BSONDocument] = None)(implicit reader: BSONDocumentReader[T], tag: ClassTag[T]): Future[List[String]] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val collection: Future[BSONCollection] = db1.map(_.collection(tag.runtimeClass.getSimpleName))
    collection.flatMap(_.distinct[String, List](fieldName, query))
  }

}
