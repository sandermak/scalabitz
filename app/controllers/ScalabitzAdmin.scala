package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import service.{ScalabitzService, BitlyService}
import service.controllers.ArticleRepository
import play.Logger
import play.api.Play
import play.api.Play.current

import org.apache.commons.codec.binary.Base64.decodeBase64
import scala.concurrent.Future

object ScalabitzAdmin extends Controller {

  val username = Play.configuration.getString("admin.username").get
  val password = Play.configuration.getString("admin.password").get

  def retrieveArticles() = Secured {
    Action {
      Async {
        Logger.info("Force retrieval of articles from bit.ly")
        BitlyService.fetchPossiblyNewArticles().map(articles => Ok(articles.toString))
      }
    }
  }

  def listPendingArticles() = Secured {
    Action {
      implicit request => // necessary to access flash-scope
        Async {
          ScalabitzService.getPendingArticles().map(articles => Ok(views.html.admin.scalabitzadmin(articles)))
        }
    }
  }

  def prePublish(id: String, action: String) = Secured {
    Action {
      Async {
        val actionFuture = action match {
          case "prepublish" => ArticleRepository.prePublishArticle(id)
          case "reject" => ArticleRepository.rejectArticle(id)
          case other => Future { Some("unsupported action") }
        }

        actionFuture.map {
          error =>
            val success = error.getOrElse("successful")
            Redirect(routes.ScalabitzAdmin.listPendingArticles()).flashing(
              "message" -> s"Action $action for $id: $success"
            )
        }
      }
    }
  }

  /**
   * Wrap an action so that it can only be executed if HTTP authorization is successful.
   * Note: this is only secure over https!
   */
  def Secured[A](action: Action[A]) = Action(action.parser) {
    request =>
      request.headers.get("Authorization").flatMap {
        authorization =>
          authorization.split(" ").drop(1).headOption.filter {
            encoded =>
              new String(decodeBase64(encoded.getBytes)).split(":").toList match {
                case u :: p :: Nil if u == username && p == password => true
                case _ => false
              }
          }.map(_ => action(request))
      }.getOrElse {
        Unauthorized.withHeaders("WWW-Authenticate" -> """Basic realm="Scalabitz Admin"""")
      }
  }


}