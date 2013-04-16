package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import service.ScalabitzService


object Application extends Controller {

  def index = Action {
    Async {
      ScalabitzService.getPublishedArticles().map(list => Ok(views.html.articles(list)))
    }
  }

  def about = Action {
    Ok(views.html.about())
  }

}