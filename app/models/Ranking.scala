package models

import reactivemongo.bson
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, Macros}

case class Ranking(
                    season: String,
                    region: String,
                    division: String,
                    position: Int,
                    team: String,
                    matches: Int,
                    wins: Int,
                    draws: Int,
                    losses: Int,
                    goalsPro: Int,
                    goalsAgainst: Int,
                    points: Int,
                    period: String
                  ) {


}

object Ranking {
  implicit def rankingReader: BSONDocumentReader[Ranking] = Macros.reader[Ranking]

  def lineToRanking(region: Region)(line: Array[String]): Ranking = {
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
      period = line(10)
    )
  }

  implicit val upsertSelector: Ranking => BSONDocument = { theRanking: Ranking =>
    bson.document(
      "season" -> theRanking.season,
      "region" -> theRanking.region,
      "division" -> theRanking.division,
      "period" -> theRanking.period,
      "team" -> theRanking.team
    )
  }

}

