package services

import javax.inject.{Inject, Singleton}

import models.Region
import play.api.Configuration

@Singleton
class RegionService @Inject()(config: Configuration) {

  def regions: Seq[Region] = config.get[Seq[Region]]("regions")

}

