package footbalisto.importing

import java.io.InputStream
import java.util.zip.ZipInputStream

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.stream.contrib.ZipInputStreamSource
import akka.stream.contrib.ZipInputStreamSource.ZipEntryData
import akka.stream.scaladsl._
import akka.util.ByteString
import footbalisto.MongoDao
import footbalisto.domain.{Ranking, Region}
import reactivemongo.bson

import scala.concurrent.Future
import scala.util.Try


object RankingImporter {


  def importRankings(region: Region) = {

    val start = System.currentTimeMillis()

    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val requestFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = s"http://static.belgianfootball.be/project/publiek/download/${region.shortName}cltdownP.zip"))

    requestFuture.map { response =>
      val bytesSource: Source[ByteString, Any] = response.entity.dataBytes
      val sink: Sink[ByteString, InputStream] = StreamConverters.asInputStream()
      val is: InputStream = bytesSource.runWith(sink)

      ZipInputStreamSource(() => new ZipInputStream(is)).map {
        case (entryData: ZipEntryData, byteString: ByteString) => byteString
      }.via(Framing.delimiter(ByteString("\n"), 1024)).drop(1).map(_.utf8String).runForeach { csvLine =>


        val line = csvLine.split(';')
        //        println(line)

        Try {
          Ranking(
            season = "1617",
            //            level = "",
            region = region.shortName,
            division = line(0),
            position = line(1).toInt,
            team = line(2),
            matches = line(3).toInt,
            wins = line(4).toInt,
            losses = line(5).toInt,
            draws = line(6).toInt,
            goalsPro = line(7).toInt,
            goalsAgainst = line(8).toInt,
            points = line(9).toInt,
            period = line(10).toInt
          )

        }.map { ranking =>

          MongoDao.upsert(bson.document(
            "season" -> ranking.season,
            "region" -> ranking.region,
            "division" -> ranking.division,
            "period" -> ranking.period,
            "team" -> ranking.team
          ), ranking)
          //          println(ranking)

        }.recover {
          case e => e.printStackTrace()
        }


        //        println(ranking)


      }


    }.map(_ => s"done after ${System.currentTimeMillis() - start}ms")


  }


}
