package footbalisto.domain

import reactivemongo.bson.{BSONDocumentReader, BSONDocumentWriter, Macros}

case class Match(
                  season: String,
                  region: String,
                  division: String,
                  //                 dateTime: DateTime,
                  home: String,
                  away: String,
                  resultHome: Option[Int],
                  resultAway: Option[Int],
                  status: String,
                  matchDay: Int,
                  regNumberHome: Int,
                  regNumberAway: Int
                ) extends Model {


  override def collection: String = this.getClass.getSimpleName
}

object Match {

  implicit def rankingWriter: BSONDocumentWriter[Match] = Macros.writer[Match]

  implicit def rankingReader: BSONDocumentReader[Match] = Macros.reader[Match]
}

