package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import service.BitlyArticleService

object Application extends Controller {
  
  def index = Action {
    Async {
      BitlyArticleService.getArticles().map(articles => Ok(views.html.index(articles)))
    }
  }

}