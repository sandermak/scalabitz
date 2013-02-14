package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits._
import concurrent.Future
import service.BitlyArticleService

object Scalabitz extends Controller {

  def index() = Action {
    Async {
      BitlyArticleService.getArticles().map(articles => Ok(articles.toString))
    }
  }



}