package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import concurrent.Future
import service.{ScalabitzService, BitlyService}
import service.controllers.ArticleRepository
import play.Logger
import play.api.Play
import play.api.Play.current

import org.apache.commons.codec.binary.Base64.decodeBase64

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

  def listAllArticles() = Secured {
    Action { implicit request =>
      Async {
        ScalabitzService.getPendingArticles().map(articles => Ok(views.html.scalabitzadmin(articles)))
      }
    }
  }

  def prePublish(id: String, action: String) = Secured {
    Action {
      Logger.info(s"Put article $id in publishing queue")
      action match {
        case "prepublish" => ArticleRepository.prePublishArticle(id)
                             Redirect("/allarticles").flashing(
                                "message" -> s"Article $id has been put in the publishing queue"
                             )
        case "reject"     => ArticleRepository.rejectArticle(id)
                             Redirect("/allarticles").flashing(
                               "message" -> s"Article $id has been rejected"
                             )
      }

    }
  }

  def Secured[A](action: Action[A]) = Action(action.parser) { request =>
    request.headers.get("Authorization").flatMap { authorization =>
      authorization.split(" ").drop(1).headOption.filter { encoded =>
        new String(decodeBase64(encoded.getBytes)).split(":").toList match {
          case u :: p :: Nil if u == username && p == password => true
          case _ => false
        }
      }.map(_ => action(request))
    }.getOrElse {
      Unauthorized.withHeaders("WWW-Authenticate" -> """Basic realm="Secured"""")
    }
  }



}