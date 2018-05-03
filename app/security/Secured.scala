package security

import play.api.mvc.Security.WithAuthentication
import play.api.mvc._
import services.UserService

trait Secured {

  val userService: UserService

  def authenticatedRequest(f: => Request[AnyContent] => Result): EssentialAction = {
    WithAuthentication[models.User] { (request: RequestHeader) =>
      val matcher = "Bearer (.*)".r
      request.headers.get("Authorization").flatMap {
        case matcher(token) => userService.findUserForToken(token)
        case _ => None
      }
    } { user =>
      Action { request =>
        f(request)
      }

    }

  }

}
