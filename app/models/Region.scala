package models

import com.typesafe.config.Config
import play.api.ConfigLoader

import scala.collection.JavaConverters

case class Region(shortName: String, fullName: String)


object Region {

  implicit val configSeqLoader: ConfigLoader[Seq[Region]] = (rootConfig: Config, path: String) => {
    import JavaConverters._
    rootConfig.getConfigList(path)
      .asScala
      .map { config =>
        Region(
          shortName = config.getString("shortName"),
          fullName = config.getString("fullName")
        )
      }


  }

}
