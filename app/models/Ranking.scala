package models

import reactivemongo.bson.{BSONDocumentReader, Macros}

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
                    period: Int
                  ) {


}

object Ranking {
  implicit def rankingReader: BSONDocumentReader[Ranking] = Macros.reader[Ranking]
}

