package services

import javax.inject.{Inject, Singleton}

import actors.{ImportActor, ImportMatches}
import akka.actor.{ActorRef, ActorSystem}
import models.{Match, Ranking}
import play.api.inject.ApplicationLifecycle
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.indexes.IndexType

import scala.concurrent.ExecutionContext

@Singleton
class InitService @Inject()(appLifecycle: ApplicationLifecycle,
                            reactiveMongoApi: ReactiveMongoApi,
                            system: ActorSystem,
                            implicit val ec: ExecutionContext
                           ) {



//  val importActorRef: ActorRef = system.actorOf(ImportActor.props(importService))
//
//
//  import scala.concurrent.duration._
//  system.scheduler.schedule(0 seconds, 5 seconds, importActorRef, ImportMatches())

}