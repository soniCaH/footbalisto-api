package controllers

import java.util.{Date, UUID}

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import models._
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.{Calendar, Dur}
import org.joda.time.DateTime
import play.api.Logger
import play.api.http.HttpEntity
import play.api.i18n.{Lang, Langs, MessagesApi}
import play.api.libs.Files.DefaultTemporaryFileCreator
import play.api.libs.json.Json
import play.api.libs.ws._
import play.api.mvc._
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.Cursor
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson
import reactivemongo.bson.Macros.handler
import reactivemongo.bson.{BSONDocument, BSONDocumentHandler, BSONString, BSONValue, document}
import security.Secured
import services.{ImportService, MongoService, RegionService, UserService}
import utils.EditDistance

import scala.concurrent.{ExecutionContext, Future}

trait JsonFormats {
  // Generates Writes and Reads for Feed and User thanks to Json Macros
  implicit val rankingFormat = Json.format[Ranking]
  implicit val matchFormat = Json.format[Match]
  implicit val regionFormat = Json.format[Region]
  implicit val idFormat = Json.format[Id]
  implicit val userFormat = Json.format[User]
}

trait BsonFormats {
  implicit val matchHandler: BSONDocumentHandler[Match] = handler[Match]
  implicit val rankingHandler: BSONDocumentHandler[Ranking] = handler[Ranking]
  implicit val inputFileFormat: BSONDocumentHandler[InputFile] = handler[InputFile]
  implicit val idHandler: BSONDocumentHandler[Id] = handler[Id]
  implicit val userHandler: BSONDocumentHandler[User] = handler[User]
}

@Singleton
class ApiController @Inject()(langs: Langs, messagesApi: MessagesApi,
                              val reactiveMongoApi: ReactiveMongoApi,
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
    Ok(Json.toJson(List(Map("name" -> "1718"), Map("name" -> "1819"), Map("name" -> "1920"), Map("name" -> "2021"))))
  }

  def availableRankingsForRegion(season: String, region: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    rankingsDao.distinct(
      "division", Option(document("season" -> season, "region" -> region))
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

  def divisionsForTeam(season: String, region: String, team: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    matchesDao.distinct(
      "division", Option(document("season" -> season, "region" -> region, "$or" -> bson.array(document("regNumberHome" -> team), document("regNumberAway" -> team))))
    ).map { s: List[String] =>
      Ok(Json.toJson(s))
    }
  }

  def matchesForTeamAndDivision(season: String, region: String, team: String, division: String): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    matchesDao.find(
      document("season" -> season, "region" -> region, "division" -> division, "$or" -> bson.array(document("regNumberHome" -> team), document("regNumberAway" -> team)))
    ).map { matches: Seq[Match] =>
      Ok(Json.toJson(matches))
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
    ).map { matches: Seq[Match] =>

      val previousMatches = matches.groupBy(_.division).flatMap { case (division, matches) =>
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
    ).map { matches: Seq[Match] =>

      val upcomingMatches = matches.groupBy(_.division).flatMap { case (division, matches) =>
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

      ws.url(url).get().map { response: WSResponse =>
        val outputStream = java.nio.file.Files.newOutputStream(tempFile.toPath)
        val sink = Sink.foreach[ByteString] { bytes =>
          outputStream.write(bytes.toArray)
        }
        val source = response.bodyAsSource
        source.runWith(sink)

        Result(
          header = ResponseHeader(200, Map.empty),
          body = HttpEntity.Streamed(source, None, Some("image/jpeg"))
        )
      }
    }
  }

  def matchesCalendar(season: String, regNumber: String, side: String, filterTeams: String) = Action.async { implicit request: Request[AnyContent] =>
    val lang: Lang = langs.availables.head
    val orQuery: BSONValue = side match {
      case "home" => reactivemongo.bson.array(document("regNumberHome" -> regNumber))
      case "away" => reactivemongo.bson.array(document("regNumberAway" -> regNumber))
      case _ => reactivemongo.bson.array(document("regNumberHome" -> regNumber), document("regNumberAway" -> regNumber)
      )
    }
    matchesDao.find(
      if (filterTeams.nonEmpty) {
        document("season" -> season,
          "$or" -> orQuery,
          "division" -> document("$in" -> filterTeams.split(",").map(_.trim).filter {
            _.length > 0
          }))
      } else {
        document("season" -> season, "$or" -> orQuery)
      }

    ).map { matches: Seq[Match] =>
      import net.fortuna.ical4j.model.component.VEvent
      import net.fortuna.ical4j.model.property.{CalScale, ProdId, Version}

      val calendar = new Calendar()
      calendar.getProperties.add(new ProdId(s"-//Footbalisto//Upcoming matches for $regNumber//EN"))
      calendar.getProperties.add(Version.VERSION_2_0)
      calendar.getProperties.add(CalScale.GREGORIAN)

      matches.foreach { m: Match =>
        val summary = if (!m.status.isEmpty) {
          s"[${m.division}] ${m.home} vs ${m.away} --- ${messagesApi(s"match.status.${m.status}")(lang)} ---"
        } else {
          (for {
            resultHome <- m.resultHome
            resultAway <- m.resultAway
          } yield {
            s"[${m.division}] ${m.home} [ $resultHome-$resultAway ] ${m.away}"
          }).getOrElse(s"[${m.division}] ${m.home} vs ${m.away}")

        }

        val matchEvent = new VEvent(new net.fortuna.ical4j.model.DateTime(m.dateTime), new Dur("PT105M"), summary)
        matchEvent.getProperties.add(new Uid(UUID.randomUUID().toString))
        calendar.getComponents.add(matchEvent)
      }
      Ok(calendar.toString).as("text/calendar")
    }
  }

  def authenticated(): EssentialAction = authenticatedRequest("Admin") { (request, user) =>
    Logger.info("in the authenticated request")
    Ok("Authenticated")
  }


  def session() = authenticatedRequest() { (request, user) =>
    Ok(Json.toJson(
      user
    ))

  }

  def users() = Action.async {
    userService.findAll().map { users =>
      Ok(Json.toJson(users))
    }
  }

  def test() = Action.async {
    reactiveMongoApi.database.map(_.collection("matches")).flatMap { collection: BSONCollection =>
      import collection.BatchCommands.AggregationFramework.{AddFieldToSet, Group}
      collection.aggregatorContext[BSONDocument](Group(BSONString("$regNumberHome"))("teamNames" -> AddFieldToSet("home")))
        .prepared.cursor
        .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())
        .map { documentList =>
          for {
            document <- documentList
            regNumber: String <- document.getAs[String]("_id")
            teamNames: List[String] <- document.getAs[List[String]]("teamNames")

          } yield (regNumber, teamNames.minBy(_.length))
        }
    }.map { regNumberToTeamName =>
      println("test")
      Ok(Json.toJson(regNumberToTeamName))
    }
  }

  def meta(season: String, region: String, division: String, regNumber: String) = Action.async {
    val x = for {
      rankings <- rankingsDao.find(document("season" -> season, "region" -> region, "division" -> division))
      matches <- matchesDao.find(
        document(
          "season" -> season,
          "division" -> division,
          "$or" -> reactivemongo.bson.array(
            document("regNumberHome" -> regNumber),
            document("regNumberAway" -> regNumber)
          )))

    } yield {
      val teamNameFromMatches = matches.find(_.regNumberHome == regNumber).map(_.home).orElse(matches.find(_.regNumberAway == regNumber).map(_.away))

      val nextMatch = matches.sortBy(_.dateTime).find(_.dateTime.after(new Date()))
      val previousMatch = matches.sortBy(_.dateTime).reverse.find(_.dateTime.before(new Date()))

      Json.obj(
        "ranking" -> rankings.sortBy(ranking => EditDistance.editDist(ranking.team, teamNameFromMatches.getOrElse(""))).headOption,
        "next" -> nextMatch.map { nm =>
          Json.obj(
            "side" -> (if (nm.regNumberHome == regNumber) "home" else "away"),
            "opponent" -> Json.obj(
              "team" -> (if (nm.regNumberHome == regNumber) nm.away else nm.home),
              "regNumber" -> (if (nm.regNumberHome == regNumber) nm.regNumberAway else nm.regNumberHome),
              "ranking" -> rankings.sortBy(ranking => EditDistance.editDist(ranking.team, if (nm.regNumberHome == regNumber) nm.away else nm.home)).headOption
            ),
            "match" -> nm
          )
        },
        "previous" -> previousMatch.map { pm =>
          Json.obj(
            "side" -> (if (pm.regNumberHome == regNumber) "home" else "away"),
            "opponent" -> Json.obj(
              "team" -> (if (pm.regNumberHome == regNumber) pm.away else pm.home),
              "regNumber" -> (if (pm.regNumberHome == regNumber) pm.regNumberAway else pm.regNumberHome),
              "ranking" -> rankings.sortBy(ranking => EditDistance.editDist(ranking.team, if (pm.regNumberHome == regNumber) pm.away else pm.home)).headOption
            ),
            "match" -> pm
          )
        },
      )
    }
    x.map(y => Ok(y))
  }
}
