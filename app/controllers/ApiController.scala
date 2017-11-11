package controllers

import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import models.{InputFile, Match, Ranking, Region}
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.libs.json.Json
import play.api.libs.ws._
import play.api.mvc._
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.bson.BSONDocumentHandler
import reactivemongo.bson.Macros.handler
import services.{ImportService, MongoService, RegionService}

import scala.concurrent.ExecutionContext

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
}

@Singleton
class ApiController @Inject()(val reactiveMongoApi: ReactiveMongoApi,
                              val cc: ControllerComponents,
                              regionService: RegionService,
                              ws: WSClient,
                              temporaryFileCreator: DefaultTemporaryFileCreator,
                              importService: ImportService,
                              implicit val mat: Materializer,
                              implicit val ec: ExecutionContext
                             ) extends AbstractController(cc) with ReactiveMongoComponents with JsonFormats with BsonFormats {

  private val rankingsDao: MongoService[Ranking] = new MongoService[Ranking](reactiveMongoApi.database, "rankings")
  private val matchesDao: MongoService[Match] = new MongoService[Match](reactiveMongoApi.database, "matches")

  def seasons() = Action {
    Ok(Json.toJson(List(Map("name" -> "1718"))))
  }

  def availableRankingsForRegion(season: String, region: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    rankingsDao.distinct(
      "division",
      Option(reactivemongo.bson.document("season" -> season, "region" -> region))
    ).map { s: List[String] =>
      Ok(Json.toJson(s))
    }
  }

  def rankings(season: String, region: String, division: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    rankingsDao.find(
      reactivemongo.bson.document("season" -> season, "region" -> region, "division" -> division)
    ).map { rankings: Seq[Ranking] =>
      Ok(Json.toJson(rankings))
    }
  }

  def availablePeriodsForDivision(season: String, region: String, division: String): Action[AnyContent] = Action.async {
    rankingsDao.distinct(
      "period",
      Option(reactivemongo.bson.document("season" -> season, "region" -> region, "division" -> division))
    ).map { s: List[String] => Ok(Json.toJson(s)) }
  }

  def rankingForDivisionAndPeriod(season: String, region: String, division: String, period: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    rankingsDao.find(
      reactivemongo.bson.document("season" -> season, "region" -> region, "division" -> division, "period" -> period)
    ).map { rankings: Seq[Ranking] =>
      Ok(Json.toJson(rankings))
    }
  }

  def matches(season: String, region: String, division: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    matchesDao.find(
      reactivemongo.bson.document("season" -> season, "region" -> region, "division" -> division)
    ).map { rankings: Seq[Match] =>
      Ok(Json.toJson(rankings))
    }
  }

  def regions(season: String) = Action {
    Ok(Json.toJson(regionService.regions))
  }

}
