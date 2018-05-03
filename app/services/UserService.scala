package services

import javax.inject.Inject

import com.typesafe.config.Config
import controllers.BsonFormats
import models.User
import play.modules.reactivemongo.ReactiveMongoApi

import scala.concurrent.{Await, ExecutionContext}

class UserService @Inject()(reactiveMongoApi: ReactiveMongoApi,
                            config: Config,
                            implicit val ec: ExecutionContext) extends BsonFormats {

  def userDao = new MongoService[User](reactiveMongoApi.database, "users")

  def findUserForToken(token: String): Option[models.User] = {
    val usersForToken = userDao.find(reactivemongo.bson.document("token" -> token))
    import scala.concurrent.duration._
    Await.result(usersForToken, 100 milliseconds).headOption

  }


}
