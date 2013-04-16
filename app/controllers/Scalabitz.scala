package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import concurrent.Future
import service.{ScalabitzService, BitlyService}
import service.controllers.ArticleRepository
import play.Logger
import play.api.Play
import play.api.Play.current

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
    Action {
      Async {
        ScalabitzService.getAllArticles().map(articles => Ok(views.html.publish(articles)))
      }
    }
  }

  def publish(id: String) = Secured {
    Action {
      Logger.info(s"Manually publish article $id")
      ArticleRepository.publishArticle(id)
      Ok(s"published $id")
    }
  }

  def Secured[A](action: Action[A]) = Action(action.parser) { request =>
    request.headers.get("Authorization").flatMap { authorization =>
      authorization.split(" ").drop(1).headOption.filter { encoded =>
        new String(org.apache.commons.codec.binary.Base64.decodeBase64(encoded.getBytes)).split(":").toList match {
          case u :: p :: Nil if u == username && p == password => true
          case _ => false
        }
      }.map(_ => action(request))
    }.getOrElse {
      Unauthorized.withHeaders("WWW-Authenticate" -> """Basic realm="Secured"""")
    }
  }



}