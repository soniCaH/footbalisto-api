package services

import models.{InputFile, Ranking}
import play.api.Logger
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DefaultDB}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, document}

import scala.concurrent.{ExecutionContext, Future}


class MongoService[T](database: Future[DefaultDB], val collectionType: String)(implicit executionContext: ExecutionContext) {


  def count(selector: BSONDocument): Future[Int] = {
    val collection: Future[BSONCollection] = database.map(_.collection(collectionType))
    collection.flatMap(_.count(Option(selector)))
  }

  def find(query: BSONDocument)(implicit reader: BSONDocumentReader[T]): Future[Seq[T]] = {
    val collection: Future[BSONCollection] = database.map(_.collection(collectionType))
    collection.flatMap(_.find(query).cursor[T]().collect(-1, Cursor.ContOnError[Seq[T]]()))
  }

  def findAll()(implicit reader: BSONDocumentReader[T]): Future[Seq[T]] = {
    val collection: Future[BSONCollection] = database.map(_.collection(collectionType))
    collection.flatMap(_.find(document()).cursor[T]().collect(-1, Cursor.ContOnError[Seq[T]]()))
  }

  def upsert(selector: BSONDocument, entity: T)(implicit writer: BSONDocumentWriter[T]): Future[Unit] = {
    val collection: Future[BSONCollection] = database.map(_.collection(collectionType))
    collection.flatMap(_.update(selector, entity, upsert = true).map(_ => {}))
  }

  def distinct(fieldName: String, query: Option[BSONDocument] = None)(implicit reader: BSONDocumentReader[T]): Future[List[String]] = {
    val collection: Future[BSONCollection] = database.map(_.collection(collectionType))
    collection.flatMap(_.distinct[String, List](fieldName, query))
  }

  def ensureIndex(indexes: Seq[(String, IndexType)]): Future[Unit] = {
    val collection: Future[BSONCollection] = database.map(_.collection(collectionType))
    collection.flatMap(_.indexesManager.ensure(Index(indexes))).map(a => {
      Logger.info(s"ensured index for $collectionType: $a")
    })
  }

}


