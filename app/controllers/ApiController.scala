package controllers

import javax.inject.{Inject, Singleton}

import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.stream.Materializer
import models.{InputFile, Match, Ranking, Region}
import play.api.Logger
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.libs.json.Json
import play.api.libs.ws._
import play.api.mvc._
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.bson
import reactivemongo.bson.Macros.{reader, writer}
import reactivemongo.bson.{BSONDocument, BSONDocumentHandler, BSONDocumentReader, BSONDocumentWriter, Macros}
import reactivemongo.play.json.BSONFormats.BSONDocumentFormat
import services.{ImportService, MongoService, RegionService}

import scala.concurrent.{ExecutionContext, Future}

trait JsonFormats {

  import play.api.libs.json.Json

  // Generates Writes and Reads for Feed and User thanks to Json Macros
  implicit val rankingFormat = Json.format[Ranking]
  implicit val matchFormat = Json.format[Match]
  implicit val regionFormat = Json.format[Region]
}

trait BsonFormats {
  implicit def matchesWriter: BSONDocumentWriter[Match] = writer[Match]

  implicit val matchesReader: BSONDocumentReader[Match] = reader[Match]

  implicit def rankingWriter: BSONDocumentWriter[Ranking] = writer[Ranking]

  implicit val rankingReader: BSONDocumentReader[Ranking] = reader[Ranking]
//  implicit val inputFileWriter: BSONDocumentWriter[InputFile] = writer[InputFile]
//  implicit val inputFileReader: BSONDocumentReader[InputFile] = reader[InputFile]

  implicit val inputFileFormat: BSONDocumentHandler[InputFile] = Macros.handler[InputFile]


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


  def seasons() = Action {

    Ok(Json.toJson(List(Map("name" -> "1617"))))
  }

  private val rankingsDao: MongoService[Ranking] = new MongoService[Ranking](reactiveMongoApi.database, "rankings")
  private val matchesDao: MongoService[Match] = new MongoService[Match](reactiveMongoApi.database, "matches")

  def availableRankingsForRegion(season: String, region: String) = Action.async { implicit request: Request[AnyContent] =>
    val divisionsFuture = rankingsDao.distinct("division", Option(reactivemongo.bson.document("season" -> season, "region" -> region)))
    divisionsFuture.map { s: List[String] => Ok(Json.toJson(s)) }
  }

  def rankings(season: String, region: String, division: String) = Action.async { implicit request: Request[AnyContent] =>
    rankingsDao.find(reactivemongo.bson.document("season" -> season, "region" -> region, "division" -> division)).map { rankings: Seq[Ranking] =>
      Ok(Json.toJson(rankings))
    }

  }

  def matches(season: String, region: String, division: String) = Action.async { implicit request: Request[AnyContent] =>
      matchesDao.find(reactivemongo.bson.document("season" -> season, "region" -> region, "division" -> division)).map { rankings: Seq[Match] =>
      Ok(Json.toJson(rankings))
    }
//    val t: Future[Seq[Match]] = new MongoService[Match](reactiveMongoApi.database, "matches").findAll()
//
//    t.map(println)
//    //    Ok(Json.toJson(List(t)))
//
//    //    val m = Future { Match("", "","", "","", None,None, "",1, 1,1 ) }
//    t.map {
//      Json.toJson(_)
//    }.map {
//      Ok(_)
//    }

//    ???
  }


  def regions(season: String) = Action {
    Ok(Json.toJson(regionService.regions))
  }


  def test() = Action {

//    val lineToRanking: (Region, Array[String]) => Ranking = { (region: Region, line: Array[String]) =>
//      Ranking(
//        season = "1617",
//        region = region.shortName,
//        division = line(0),
//        position = line(1).toInt,
//        team = line(2),
//        matches = line(3).toInt,
//        wins = line(4).toInt,
//        losses = line(5).toInt,
//        draws = line(6).toInt,
//        goalsPro = line(7).toInt,
//        goalsAgainst = line(8).toInt,
//        points = line(9).toInt,
//        period = line(10).toInt
//      )
//    }
//
//    val rankingUpsortSelector: Ranking => BSONDocument = { theRanking: Ranking =>
//      bson.document(
//        "season" -> theRanking.season,
//        "region" -> theRanking.region,
//        "division" -> theRanking.division,
//        "period" -> theRanking.period,
//        "team" -> theRanking.team
//      )
//    }
//
//    val lineToMatch: (Region, Array[String]) => Match = { (region: Region, line: Array[String]) =>
//      def safeStringToLong(str: String): Option[Int] = {
//        import scala.util.control.Exception._
//        catching(classOf[NumberFormatException]) opt str.toInt
//      }
//
//      Match(
//        season = "1617",
//        region = region.shortName,
//        division = line(0).trim(),
//        //dateTime = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm").parseDateTime(s"${line(1)} ${line(2)}"),
//        home = line(3).trim(),
//        away = line(4).trim(),
//        resultHome = safeStringToLong(line(5).trim()),
//        resultAway = safeStringToLong(line(6).trim()),
//        status = line(7).trim(),
//        matchDay = line(8).toInt,
//        regNumberHome = line(9).toInt,
//        regNumberAway = line(10).toInt
//      )
//    }
//
//    val matchUpsortSelector: Match => BSONDocument = { theMatch: Match =>
//
//      bson.document(
//        "season" -> theMatch.season,
//        "region" -> theMatch.region,
//        "division" -> theMatch.division,
//        "matchDay" -> theMatch.matchDay,
//        "home" -> theMatch.home,
//        "away" -> theMatch.away,
//        "regNumberHome" -> theMatch.regNumberHome,
//        "regNumberAway" -> theMatch.regNumberAway
//        //            "team" -> ranking.team
//      )
//
//
//
//    }

//    importService.importRankings[Ranking]("nat", new MongoService[Ranking](reactiveMongoApi.database, "rankings"), lineToRanking, rankingUpsortSelector, (region: Region) => s"http://static.belgianfootball.be/project/publiek/download/${region.shortName}cltdownP.zip")
//    importService.importRankings[Match]("nat", new MongoService[Match](reactiveMongoApi.database, "matches"), lineToMatch, matchUpsortSelector, (region: Region) => s"http://static.belgianfootball.be/project/publiek/download/${region.shortName}resdownP.zip")

    val countFuture = new MongoService[InputFile](reactiveMongoApi.database, "inputfiles").count(bson.document("sha256Hash" -> "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
    countFuture.onComplete { i => Logger.error(s"count for hash: ${i}")}

    val findFuture = new MongoService[InputFile](reactiveMongoApi.database, "inputfiles").find(bson.document("sha256Hash" -> "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
    findFuture.onComplete { res =>
    Logger.error(s"find result: ${res}")

    }


    Ok("")

  }

}
