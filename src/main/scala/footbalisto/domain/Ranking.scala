package footbalisto.domain

import akka.http.scaladsl
//import akka.http.scaladsl.model
import reactivemongo.bson.{BSONDocumentReader, BSONDocumentWriter, Macros}

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
                  ) extends Model {
  override def collection = this.getClass.getSimpleName
}


object Ranking {

  implicit def collectionProvider: Model => String = _ => "ranking"

  implicit def rankingWriter: BSONDocumentWriter[Ranking] = Macros.writer[Ranking]

  implicit def rankingReader: BSONDocumentReader[Ranking] = Macros.reader[Ranking]
}

