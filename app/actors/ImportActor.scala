package actors

import akka.actor.{Actor, Props}
import controllers.BsonFormats
import models.Match.lineToMatch
import models.Ranking.lineToRanking
import models.{Match, Ranking, Region}
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoApi
import services.{ImportService, MongoService}

import scala.concurrent.ExecutionContext.Implicits.global


class ImportActor(importService: ImportService, reactiveMongoApi: ReactiveMongoApi) extends Actor with BsonFormats {


  override def receive: Receive = {
    case ImportMatches(region) => {
      Logger.info(s"got a tick to import the matches for region: $region")
      importService.importEntities[Match](region, new MongoService[Match](reactiveMongoApi.database, "matches"), lineToMatch(region), (region: Region) => s"http://static.belgianfootball.be/project/publiek/download/${region.shortName}resdownP.zip")
    }
    case ImportRankings(region) => {
      Logger.info(s"got a tick to import the rankings for region: $region")
      importService.importEntities[Ranking](region, new MongoService[Ranking](reactiveMongoApi.database, "rankings"), lineToRanking(region), (region: Region) => s"http://static.belgianfootball.be/project/publiek/download/${region.shortName}cltdownP.zip")
    }
  }
}

sealed trait Messages
case class ImportMatches(region: Region) extends Messages
case class ImportRankings(region: Region) extends Messages

object ImportActor {
  def props(importService: ImportService, reactiveMongoApi: ReactiveMongoApi) = Props(new ImportActor(importService, reactiveMongoApi))
}