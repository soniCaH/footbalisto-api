package security

import models.User
import play.Logger
import play.api.mvc.Security.WithAuthentication
import play.api.mvc._
import services.UserService

trait Secured {

  val userService: UserService

  def authenticatedRequest(role: String = "")(f: => (Request[AnyContent], User) => Result): EssentialAction = {
    WithAuthentication[models.User] { (request: RequestHeader) =>
      val matcher = "Bearer (.*)".r
      request.headers.get("Authorization").flatMap {
        case matcher(token) => {
          Logger.info(s"the token: $token")
          userService.findUserForToken(token).filter {
            role.isEmpty || _.roles.contains(role)
          }
        }
        case _ => None
      }
    } { user: User =>
      Action { request =>
        f(request, user)
      }

    }

  }

}
