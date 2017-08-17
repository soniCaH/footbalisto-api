package models

import reactivemongo.bson.{BSONDocumentReader, Macros}

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
                ) {

  implicit def matchReader: BSONDocumentReader[Match] = Macros.reader[Match]
}

