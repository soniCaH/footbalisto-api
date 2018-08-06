package models

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import reactivemongo.bson
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, Macros}

case class Match(
                  season: String,
                  region: String,
                  division: String,
                  dateTime: Date,
                  home: String,
                  away: String,
                  resultHome: Option[Int],
                  resultAway: Option[Int],
                  status: String,
                  matchDay: Int,
                  regNumberHome: String,
                  regNumberAway: String
                ) {

  implicit def matchReader: BSONDocumentReader[Match] = Macros.reader[Match]
}

object Match {

  val dateParser = new SimpleDateFormat("dd/MM/yyyy HH:mm")
  dateParser.setTimeZone(TimeZone.getTimeZone("Europe/Brussels"))

  def lineToMatch(region: Region)(line: Array[String]): Match = {
    def safeStringToLong(str: String): Option[Int] = {
      import scala.util.control.Exception._
      catching(classOf[NumberFormatException]) opt str.toInt
    }

    Match(
      season = "1819",
      region = region.shortName,
      division = line(0).trim(),
      dateTime = dateParser.parse(s"${line(1)} ${line(2)}"), //DateTimeFormat.forPattern("dd/MM/yyyy HH:mm").parseDateTime(s"${line(1)} ${line(2)}"),
      home = line(3).trim(),
      away = line(4).trim(),
      resultHome = safeStringToLong(line(5).trim()),
      resultAway = safeStringToLong(line(6).trim()),
      status = line(7).trim(),
      matchDay = line(8).toInt,
      regNumberHome = line(9),
      regNumberAway = line(10)
    )
  }

  implicit val upsortSelector: Match => BSONDocument = { theMatch: Match =>

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

