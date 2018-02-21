package controllers

import java.util.Date
import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import models._
import org.joda.time.DateTime
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.libs.json.Json
import play.api.libs.ws._
import play.api.mvc._
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.bson.Macros.handler
import reactivemongo.bson.{BSONDocumentHandler, document}
import security.Secured
import services.{ImportService, MongoService, RegionService, UserService}

import scala.concurrent.{ExecutionContext, Future}

trait JsonFormats {
  // Generates Writes and Reads for Feed and User thanks to Json Macros
  implicit val rankingFormat = Json.format[Ranking]
  implicit val matchFormat = Json.format[Match]
  implicit val regionFormat = Json.format[Region]
}

trait BsonFormats {
  implicit val matchHandler: BSONDocumentHandler[Match] = handler[Match]
  implicit val rankingHandler: BSONDocumentHandler[Ranking] = handler[Ranking]
  implicit val inputFileFormat: BSONDocumentHandler[InputFile] = handler[InputFile]
  implicit val userFormat: BSONDocumentHandler[User] = handler[User]
}

@Singleton
class ApiController @Inject()(val reactiveMongoApi: ReactiveMongoApi,
                              val cc: ControllerComponents,
                              regionService: RegionService,
                              val userService: UserService,
                              ws: WSClient,
                              temporaryFileCreator: DefaultTemporaryFileCreator,
                              importService: ImportService,
                              implicit val mat: Materializer,
                              implicit val ec: ExecutionContext
                             ) extends AbstractController(cc) with ReactiveMongoComponents with JsonFormats with BsonFormats with Secured {

  private val rankingsDao: MongoService[Ranking] = new MongoService[Ranking](reactiveMongoApi.database, "rankings")
  private val matchesDao: MongoService[Match] = new MongoService[Match](reactiveMongoApi.database, "matches")

  def seasons() = Action {
    Ok(Json.toJson(List(Map("name" -> "1718"))))
  }

  def availableRankingsForRegion(season: String, region: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    rankingsDao.distinct(
      "division",
      Option(document("season" -> season, "region" -> region))
    ).map { s: List[String] =>
      Ok(Json.toJson(s))
    }
  }

  def rankings(season: String, region: String, division: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    rankingsDao.find(
      document("season" -> season, "region" -> region, "division" -> division)
    ).map { rankings: Seq[Ranking] =>
      Ok(Json.toJson(rankings))
    }
  }

  def availablePeriodsForDivision(season: String, region: String, division: String): Action[AnyContent] = Action.async {
    rankingsDao.distinct(
      "period",
      Option(document("season" -> season, "region" -> region, "division" -> division))
    ).map { s: List[String] => Ok(Json.toJson(s)) }
  }

  def rankingForDivisionAndPeriod(season: String, region: String, division: String, period: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    rankingsDao.find(
      document("season" -> season, "region" -> region, "division" -> division, "period" -> period)
    ).map { rankings: Seq[Ranking] =>
      Ok(Json.toJson(rankings))
    }
  }

  def matches(season: String, region: String, division: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    matchesDao.find(
      document("season" -> season, "region" -> region, "division" -> division)
    ).map { matches: Seq[Match] =>
      Ok(Json.toJson(matches))
    }
  }

  def matchesForMatchDay(season: String, region: String, division: String, matchDay: Long): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>

    matchesDao.find(
      document("season" -> season, "region" -> region, "division" -> division, "matchDay" -> matchDay)
    ).map { rankings: Seq[Match] =>
      Ok(Json.toJson(rankings))
    }
  }

  def matchesForTeamMatchDay(season: String, region: String, division: String, regNumber: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>

    matchesDao.find(
      document(
        "season" -> season,
        "region" -> region,
        "division" -> division,
      )
    ).map { matches: Seq[Match] =>
      val matchesForNextMatchday = matches
        .filter { m => m.regNumberHome == regNumber || m.regNumberAway == regNumber }
        .filter { m => m.dateTime after new Date() }
        .sortBy(_.dateTime.getTime).headOption.map(_.matchDay)
        .map { matchDay =>
          matches.filter(_.matchDay == matchDay)
        }
      Ok(Json.toJson(matchesForNextMatchday))
    }
  }

  def regions(season: String) = Action {
    Ok(Json.toJson(regionService.regions))
  }

  def previousMatches(season: String, regNumber: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    matchesDao.find(
      document("season" -> season, "$or" -> reactivemongo.bson.array(
        document("regNumberHome" -> regNumber),
        document("regNumberAway" -> regNumber)
      ))
    ).map { rankings: Seq[Match] =>

      val previousMatches = rankings.groupBy(_.division).flatMap { case (division, matches) =>
        matches.filter { m =>
          val now = new Date()
          (m.dateTime before now) && (m.dateTime after new DateTime(now).minusMonths(1).toDate)
        }.sortBy(-_.dateTime.getTime).headOption
      }
      Ok(Json.toJson(previousMatches))
    }
  }

  def upcomingMatches(season: String, regNumber: String) = Action.async { implicit request: Request[AnyContent] =>
    matchesDao.find(
      document("season" -> season, "$or" -> reactivemongo.bson.array(
        document("regNumberHome" -> regNumber),
        document("regNumberAway" -> regNumber)
      ))
    ).map { rankings: Seq[Match] =>

      val upcomingMatches = rankings.groupBy(_.division).flatMap { case (division, matches) =>
        matches.filter { m =>
          m.dateTime after new Date()
        }.sortBy(_.dateTime.getTime).headOption
      }
      Ok(Json.toJson(upcomingMatches))
    }
  }

  def logo(regNumber: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>

    val tempFile = new java.io.File(s"logo/$regNumber.jpeg")

    if (tempFile.exists()) {
      val source = FileIO.fromPath(tempFile.toPath)
      Future(Result(
        header = ResponseHeader(200, Map.empty),
        body = HttpEntity.Streamed(source, None, Some("image/jpeg"))
      ))

    } else {
      tempFile.getParentFile.mkdirs()

      val url = s"http://static.belgianfootball.be/project/publiek/clublogo/$regNumber.jpg"
      ws.url(url).get().flatMap { response: WSResponse =>
        val outputStream = java.nio.file.Files.newOutputStream(tempFile.toPath)
        // The sink that writes to the output stream
        val sink = Sink.foreach[ByteString] { bytes =>
          outputStream.write(bytes.toArray)
        }
        response.bodyAsSource.runWith(sink)
      }.map { done =>
        val source = FileIO.fromPath(tempFile.toPath)
        Result(
          header = ResponseHeader(200, Map.empty),
          body = HttpEntity.Streamed(source, None, Some("image/jpeg"))
        )
      }
    }
  }

  def authenticated(): EssentialAction = authenticatedRequest { request =>
    Logger.info("in the authenticated request")
    Ok("Authenticated")
  }


}
