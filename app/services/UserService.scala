package services

import com.typesafe.config.Config
import controllers.BsonFormats
import javax.inject.Inject
import models.User
import play.modules.reactivemongo.ReactiveMongoApi

import scala.concurrent.{Await, ExecutionContext, Future}

class UserService @Inject()(reactiveMongoApi: ReactiveMongoApi,
                            config: Config,
                            implicit val ec: ExecutionContext) extends BsonFormats {

  def userDao = new MongoService[User](reactiveMongoApi.database, "users")

  def findAll(): Future[Seq[User]] = {
    userDao.findAll()
  }

  def findUserForToken(token: String): Option[models.User] = {
    val usersForToken = userDao.find(reactivemongo.bson.document("token" -> token))
    import scala.concurrent.duration._
    Await.result(usersForToken, 100 milliseconds).headOption

  }


}
