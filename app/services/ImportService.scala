package services

import java.io.InputStream
import java.security.{DigestOutputStream, MessageDigest}
import java.util.zip.ZipInputStream
import javax.inject.{Inject, Singleton}

import actors.{ImportActor, ImportMatches, ImportRankings}
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.contrib.ZipInputStreamSource
import akka.stream.contrib.ZipInputStreamSource.ZipEntryData
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source, StreamConverters}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import com.typesafe.config.Config
import controllers.BsonFormats
import models.{InputFile, Match, Ranking, Region}
import org.apache.commons.codec.binary.Hex
import play.api.Logger
import play.api.libs.Files.{DefaultTemporaryFileCreator, TemporaryFile}
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSResponse}
import play.modules.reactivemongo.{JSONFileToSave, ReactiveMongoApi}
import reactivemongo.api.gridfs.GridFS
import reactivemongo.api.indexes.IndexType
import reactivemongo.bson
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json.JSONSerializationPack

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class ImportService @Inject()(reactiveMongoApi: ReactiveMongoApi,
                              ws: WSClient,
                              temporaryFileCreator: DefaultTemporaryFileCreator,
                              system: ActorSystem,
                              config: Config,
                              regionService: RegionService,
                              implicit val mat: Materializer,
                              implicit val ec: ExecutionContext) extends BsonFormats {

  type UpsertSelector[T] = T => BSONDocument

  def importEntities[T](region: Region, mongoService: MongoService[T], lineToEntity: (Array[String]) => T, buildResourceUrl: Region => String)(implicit writer: reactivemongo.bson.BSONDocumentWriter[T], upsertSelector: UpsertSelector[T]) = {
    val temporaryFile = temporaryFileCreator.create("filename", "tmp")
    val url = buildResourceUrl(region)
    val futureResponse: Future[WSResponse] = ws.url(url).withMethod("GET").stream()

    val downloadedFile: Future[(TemporaryFile, String)] = futureResponse.flatMap {
      res =>
        val outputStream = java.nio.file.Files.newOutputStream(temporaryFile.path)
        val digest = MessageDigest.getInstance("SHA-256")
        val digestOutputStream = new DigestOutputStream(outputStream, digest)

        // The sink that writes to the output stream
        val sink = Sink.foreach[ByteString] { bytes =>
          digestOutputStream.write(bytes.toArray)
        }

        // materialize and run the stream
        res.bodyAsSource.runWith(sink).andThen {
          case result =>
            // Close the output stream whether there was an error or not
            digestOutputStream.close()
            // Get the result or rethrow the error
            result.get
        }.map(_ => (temporaryFile, Hex.encodeHexString(digest.digest())))
    }

    downloadedFile.map { case (tempFile, sha256Hash) =>
      new MongoService[InputFile](reactiveMongoApi.database, "inputfiles").count(bson.document(
        "sha256Hash" -> sha256Hash
      )).map {
        case 0 => importFile(tempFile, sha256Hash)
        case 1 => Future {
          Logger.info(s"ignoring file with hash $sha256Hash")
        }
        case _ => Future {
          Logger.error("found more than 1 inputFile with same hash, this should nog happen")
        }
      }
    }.onComplete {
      case Success(_) =>
      case Failure(e) => Logger.error("Something went wrong", e)
    }

    def importFile(temporaryFile: TemporaryFile, sha256Hash: String): Unit = {
      val bytesSource: Source[ByteString, Future[IOResult]] = FileIO.fromPath(temporaryFile.path)

      val mongo = new MongoService[InputFile](reactiveMongoApi.database, "inputfiles")
      val inputFile = InputFile(url, mongoService.collectionType, sha256Hash)

      saveToGridFS(reactiveMongoApi.gridFS, inputFile.name, None, Enumerator.fromPath(temporaryFile.path))

      mongo.upsert(bson.document(
        "sha256Hash" -> inputFile.sha256Hash
      ), inputFile)

      val sink: Sink[ByteString, InputStream] = StreamConverters.asInputStream()
      val is: InputStream = bytesSource.runWith(sink)

      ZipInputStreamSource(() => new ZipInputStream(is)).map {
        case (_: ZipEntryData, byteString: ByteString) => byteString
      }.via(Framing.delimiter(ByteString("\n"), 1024)).drop(1).map(_.decodeString("Windows-1252")).runForeach { csvLine =>
        val line = csvLine.split(';')
        Try {
          lineToEntity(line)
        }.map { entity =>
          mongoService.upsert(upsertSelector(entity), entity)
        }.recover {
          case e =>
            Logger.error(e.getMessage, e)
        }
      }.onComplete {
        case Success(_) => Logger.info("Processed the input file")
        case Failure(e) => Logger.error("Something went wrong", e)
      }
    }
  }

  def saveToGridFS(gridfs: GridFS[JSONSerializationPack.type],
                   filename: String,
                   contentType: Option[String],
                   data: Enumerator[Array[Byte]]
                  ): Future[Unit] = {

    // Prepare the GridFS object to the file to be pushed
    val gridfsObj: JSONFileToSave = JSONFileToSave(Option(filename), contentType)
    import play.modules.reactivemongo.MongoController
    import MongoController.readFileReads
    import reactivemongo.play.json._

    val save: Future[gridfs.ReadFile[JsValue]] = gridfs.save(data, gridfsObj)

    save.map { x => Logger.info(s"saved the input file: $x") }
  }

  def ensureIndexes(): Future[Unit] = {

    def matchDao = new MongoService[Match](reactiveMongoApi.database, "matches")

    def rankingDao = new MongoService[Ranking](reactiveMongoApi.database, "rankings")

    rankingDao.ensureIndex(Seq(
      ("season", IndexType.Ascending),
      ("region", IndexType.Ascending),
      ("division", IndexType.Ascending),
      ("period", IndexType.Ascending),
      ("team", IndexType.Ascending)
    ))

    matchDao.ensureIndex(Seq(
      ("season", IndexType.Ascending),
      ("region", IndexType.Ascending),
      ("division", IndexType.Ascending),
      ("matchDay", IndexType.Ascending),
      ("home", IndexType.Ascending),
      ("away", IndexType.Ascending),
      ("regNumberHome", IndexType.Ascending),
      ("regNumberAway", IndexType.Ascending)
    ))

    matchDao.ensureIndex(Seq(
      ("regNumberHome", IndexType.Ascending)
    ))

    matchDao.ensureIndex(Seq(
      ("regNumberAway", IndexType.Ascending)
    ))
  }

  ensureIndexes()

  val importActorRef: ActorRef = system.actorOf(ImportActor.props(this, reactiveMongoApi))

  import scala.concurrent.duration._

  implicit def asFiniteDuration(d: java.time.Duration): FiniteDuration =
    scala.concurrent.duration.Duration.fromNanos(d.toNanos)

  val interval: FiniteDuration = config.getDuration("polling.interval")
  Logger.info(s"scheduling polling interval to $interval")

  var delay: Int = 0
  val spread: FiniteDuration = if (interval / regionService.regions.size > 10.seconds) {
    10.seconds
  }
  else {
    interval / regionService.regions.size
  }

  regionService.regions.foreach { region: Region =>
    val rankingsDelay = delay * spread
    val matchesDelay = (delay * spread) + (spread / 2)
    system.scheduler.schedule(rankingsDelay, interval, importActorRef, ImportRankings(region))
    system.scheduler.schedule(matchesDelay, interval, importActorRef, ImportMatches(region))
    delay = delay + 1
  }


}
