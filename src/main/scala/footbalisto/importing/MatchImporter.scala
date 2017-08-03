package footbalisto.importing

import java.io.InputStream
import java.util.zip.ZipInputStream

import akka.Done
import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.contrib.ZipInputStreamSource
import akka.stream.contrib.ZipInputStreamSource.ZipEntryData
import akka.stream.scaladsl.{Framing, Sink, Source, StreamConverters}
import akka.util.ByteString
import footbalisto.MongoDao
import footbalisto.domain.{Match, Ranking, Region}
import footbalisto.importing.MatchImporter.ImportMatches
import reactivemongo.bson

import scala.concurrent.Future
import scala.util.Try

class MatchImporter extends Actor {

  def importMatches(region: Region) = {

    val start = System.currentTimeMillis()
    var nbMatches = 0

    println("importing matches")

    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val requestFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"http://static.belgianfootball.be/project/publiek/download/${region.shortName}resdownP.zip"))

    requestFuture.map { response =>
      val bytesSource: Source[ByteString, Any] = response.entity.dataBytes
      val sink: Sink[ByteString, InputStream] = StreamConverters.asInputStream()
      val is: InputStream = bytesSource.runWith(sink)

      val t: Future[Done] = ZipInputStreamSource(() => new ZipInputStream(is)).map {
        case (entryData: ZipEntryData, byteString: ByteString) => byteString
      }.via(Framing.delimiter(ByteString("\n"), 1024)).drop(1).map(_.utf8String).runForeach { csvLine =>


        val line = csvLine.split(';')
        //        println(line)

        Try {
          def safeStringToLong(str: String): Option[Int] = {
            import scala.util.control.Exception._
            catching(classOf[NumberFormatException]) opt str.toInt
          }

          Match(
            season = "1617",
            region = region.shortName,
            division = line(0).trim(),
            //dateTime = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm").parseDateTime(s"${line(1)} ${line(2)}"),
            home = line(3).trim(),
            away = line(4).trim(),
            resultHome = safeStringToLong(line(5).trim()),
            resultAway = safeStringToLong(line(6).trim()),
            status = line(7).trim(),
            matchDay = line(8).toInt,
            regNumberHome = line(9).toInt,
            regNumberAway = line(10).toInt
          )

        }.map { theMatch =>

          MongoDao.upsert(bson.document(
            "season" -> theMatch.season,
            "region" -> theMatch.region,
            "division" -> theMatch.division,
            "matchDay" -> theMatch.matchDay,
            "home" -> theMatch.home,
            "away" -> theMatch.away,
            "regNumberHome" -> theMatch.regNumberHome,
            "regNumberAway" -> theMatch.regNumberAway
            //            "team" -> ranking.team
          ), theMatch)
          //          MongoDao.insert(theMatch)
          //                    println(theMatch)
          nbMatches += 1

        }.recover {
          case e => {
            System.err.println("KAPOTTE LIJN")
            e.printStackTrace()

          }
        }


        //        println(ranking)


      }
      t.map { (x: Done) =>
        println(s"$nbMatches processed after ${System.currentTimeMillis() - start}ms")
        x
      }


    }.map(_ => s"$nbMatches read after ${System.currentTimeMillis() - start}ms")


  }

  override def receive: Receive = {

    case ImportMatches(region: String) => ???

  }

}

object MatchImporter {

  val props = Props(classOf[MatchImporter])

  case class ImportMatches(region: String)

}
