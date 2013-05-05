package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import service.ScalabitzService
import play.api.Play.current
import play.api.Play


object Frontpage extends Controller {

  // Google Analytics properties, used in index.scala.html
  lazy val domain = Play.configuration.getString("site.ga.domain").get
  lazy val trackingid = Play.configuration.getString("site.ga.trackingid").get

  def index(page: Int) = Action {
    Async {
      val cleanPage = if (page >= 0 && page < 10) page else 0
      ScalabitzService.getPublishedArticles(cleanPage).map(list => Ok(views.html.articles(list, cleanPage)))
    }
  }

  def about = Action {
    Ok(views.html.about())
  }

}