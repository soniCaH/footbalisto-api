package footbalisto.domain

import reactivemongo.bson.{BSONDocumentReader, BSONDocumentWriter, Macros}

case class Ranking(
                    id: String,
                    season: String,
                    level: String,
                    province: String //,
                    //                    division: String,
                    //                    position: String,
                    //                    team: String,
                    //                    matches: List[String],
                    //                    wins: Int,
                    //                    draws: Int,
                    //                    losses: Int,
                    //                    goalsPro: Int,
                    //                    goalsAgainst: Int,
                    //                    points: Int,
                    //                    period: Int
                  ) extends Model {
  override def collection = "ranking"
}


object Ranking {
  implicit def rankingWriter: BSONDocumentWriter[Ranking] = Macros.writer[Ranking]

  implicit def rankingReader: BSONDocumentReader[Ranking] = Macros.reader[Ranking]
}
