package footbalisto

import java.util

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import footbalisto.domain.{Match, Ranking, Region, Season}
import footbalisto.importing.{MatchImporter, RankingImporter}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import reactivemongo.api.indexes.IndexType
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, PrettyPrinter, RootJsonFormat}

import scala.collection.convert.ImplicitConversionsToScala
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val printer = PrettyPrinter
  implicit val regionFormat = jsonFormat2(Region.apply)
  implicit val rankingFormat = jsonFormat13(Ranking.apply)
  implicit val matchFormat = jsonFormat11(Match.apply)
  val isoDateTimeFormatter = ISODateTimeFormat.basicDate()

  implicit object DateTimeFormat extends JsonFormat[DateTime] {

    //val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

    def write(date: DateTime) = JsString(
      isoDateTimeFormatter.print(date)
    )

    def read(value: JsValue) = {
      value match {
        case JsString(dateString) => DateTime.parse(dateString)
        case _ => throw new DeserializationException("String expected")
      }
    }
  }

  implicit val seasonFormat = jsonFormat3(Season.apply)


}

object FootbalistoService extends App with JsonSupport {

  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()
  val logger = Logging(system, getClass)

  if (false) {
    println("deleting rankings")
    MongoDao.deleteAll(Ranking.getClass.getSimpleName)
    println("deleting matches")
    MongoDao.deleteAll(Match.getClass.getSimpleName)
  }


  //  val regionsConfig = config.getConfig("regions")

  ensureIndexes()

  def parseRegions(): Seq[Region] = {
    import ImplicitConversionsToScala._
    config.getList("regions").map { b =>
      val unwrapped = b.unwrapped()

      val map = unwrapped.asInstanceOf[util.Map[String, String]]
      Region(map("shortName"), map("fullName"))
    }
  }

  def parseSeasons(): Seq[Season] = {

    import ImplicitConversionsToScala._
    config.getList("seasons").map { b =>
      val unwrapped = b.unwrapped()

      val map = unwrapped.asInstanceOf[util.Map[String, String]]
      //      Season(map("name"), isoDateTimeFormatter.parseDateTime(map("from")), isoDateTimeFormatter.parseDateTime(map("to")))
      Season(map("name"), DateTime.parse(map("from")), DateTime.parse(map("to")))
    }
  }

  val regions = parseRegions()
  val seasons: Seq[Season] = parseSeasons()


  println(regions)


  regions.foreach { region =>

    //    RankingImporter.importRankings(region)
    //    MatchImporter.importMatches(region)

  }

  val route =
    pathSingleSlash {
      getFromResource("index.html")
    } ~
      path("seasons") {
        cors() {
          complete(seasons)
        }
      } ~
      path("seasons" / Segment / "regions") { season: String =>
        cors() {
          complete(regions)
        }
      } ~
      path("seasons" / Segment / "regions" / Segment / "rankings" / Segment) { (season: String, region: String, division: String) =>
        cors() {
          //          complete(s"segment1: $segment1 segment2: $segment2")
          complete(MongoDao.find[Ranking](reactivemongo.bson.document("season" -> season, "region" -> region, "division" -> division)))
        }
      } ~
      path("seasons" / Segment / "regions" / Segment / "rankings") { (season: String, region: String) =>
        cors() {
          //          complete(s"segment1: $segment1 segment2: $segment2")
          complete(MongoDao.distinct[Ranking]("division", Option(reactivemongo.bson.document("season" -> season, "region" -> region))))
        }
      } ~
      path("hello") {
        cors() {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      } ~
      path("rankings" / "import") {
        post {

          complete(RankingImporter.importRankings(Region("ant", "Antwerpen")))
        }
      } ~
      path("matches" / "import") {
        post {

          complete(MatchImporter.importMatches(Region("ant", "Antwerpen")))
        }
      }


  Http().bindAndHandle(RouteResult.route2HandlerFlow(route), config.getString("http.interface"), config.getInt("http.port"))


  def ensureIndexes() = {
    //["season", "region", "division", "matchDay", "home", "away", "regNumberHome", "regNumberAway"]
    MongoDao.ensureIndex[Ranking](Seq(
      ("season", IndexType.Ascending),
      ("region", IndexType.Ascending),
      ("division", IndexType.Ascending),
      ("period", IndexType.Ascending),
      ("team", IndexType.Ascending)
    ))

    MongoDao.ensureIndex[Match](Seq(
      ("season", IndexType.Ascending),
      ("region", IndexType.Ascending),
      ("division", IndexType.Ascending),
      ("matchDay", IndexType.Ascending),
      ("home", IndexType.Ascending),
      ("away", IndexType.Ascending),
      ("regNumberHome", IndexType.Ascending),
      ("regNumberAway", IndexType.Ascending)
    ))
  }
}
