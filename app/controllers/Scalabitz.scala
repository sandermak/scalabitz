package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import concurrent.Future
import service.{ScalabitzService, BitlyArticleRetrievalService}
import service.controllers.ArticleRepository
import play.Logger

object Scalabitz extends Controller {

  def retrieveArticles() = Action {
    Async {
      Logger.info("Force retrieval of articles from bit.ly")
      BitlyArticleRetrievalService.fetchPossiblyNewArticles().map(articles => Ok(articles.toString))
    }
  }

  def listAllArticles() = Action {
    Async {
      ScalabitzService.getAllArticles().map(articles => Ok(views.html.publish(articles)))
    }
  }

  def publish(id: String) = Action {
      Logger.info("Manually publish article $id")
      ArticleRepository.publishArticle(id)
      Ok(s"published $id")
  }




}