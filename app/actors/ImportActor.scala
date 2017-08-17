package actors

import actors.ImportActor.{lineToRanking2, rankingUpsortSelector}
import akka.actor.{Actor, Props}
import controllers.BsonFormats
import models.{Match, Ranking, Region}
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.bson
import reactivemongo.bson.BSONDocument
import services.{ImportService, MongoService}


class ImportActor(importService: ImportService, reactiveMongoApi: ReactiveMongoApi) extends Actor with BsonFormats {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def receive: Receive = {
    case ImportMatches(region) => {
      Logger.info(s"got a tick to import the matches for region: $region")
      implicit val upsertSelector: importService.UpsertSelector[Match] = ImportActor.matchUpsortSelector
      importService.importEntities[Match](region, new MongoService[Match](reactiveMongoApi.database, "matches"), ImportActor.lineToMatch2(region), (region: Region) => s"http://static.belgianfootball.be/project/publiek/download/${region.shortName}resdownP.zip")
    }
    case ImportRankings(region) => {
      Logger.info(s"got a tick to import the rankings for region: $region")

      implicit val upsertSelector: importService.UpsertSelector[Ranking] = rankingUpsortSelector
      importService.importEntities[Ranking](region, new MongoService[Ranking](reactiveMongoApi.database, "rankings"), lineToRanking2(region), (region: Region) => s"http://static.belgianfootball.be/project/publiek/download/${region.shortName}cltdownP.zip")
    }
  }
}

sealed trait Messages

case class ImportMatches(region: Region) extends Messages

case class ImportRankings(region: Region) extends Messages


object ImportActor {
  def props(importService: ImportService, reactiveMongoApi: ReactiveMongoApi) = Props(new ImportActor(importService, reactiveMongoApi))

  val lineToRanking: (Region, Array[String]) => Ranking = { (region: Region, line: Array[String]) =>
    Ranking(
      season = "1617",
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
  }

  def lineToRanking2(region: Region)(line: Array[String]): Ranking = {
    Ranking(
      season = "1617",
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
  }

  val rankingUpsortSelector: Ranking => BSONDocument = { theRanking: Ranking =>
    bson.document(
      "season" -> theRanking.season,
      "region" -> theRanking.region,
      "division" -> theRanking.division,
      "period" -> theRanking.period,
      "team" -> theRanking.team
    )
  }

  def lineToMatch2(region: Region)(line: Array[String]): Match = {
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
  }

  val lineToMatch: (Region, Array[String]) => Match = { (region: Region, line: Array[String]) =>
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
  }

  val matchUpsortSelector: Match => BSONDocument = { theMatch: Match =>

    bson.document(
      "season" -> theMatch.season,
      "region" -> theMatch.region,
      "division" -> theMatch.division,
      "matchDay" -> theMatch.matchDay,
      "home" -> theMatch.home,
      "away" -> theMatch.away,
      "regNumberHome" -> theMatch.regNumberHome,
      "regNumberAway" -> theMatch.regNumberAway
      //            "team" -> ranking.team
    )


  }
}