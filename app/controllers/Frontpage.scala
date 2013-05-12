package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import service.ScalabitzService
import play.api.Play.current
import play.api.Play


object Frontpage extends Controller {

  // Google Analytics properties, used in index.scala.html
  lazy val domain = getConfig("site.ga.domain")
  lazy val trackingid = getConfig("site.ga.trackingid")
  lazy val adclient = getConfig("site.adwords.adclient")
  lazy val adslot = getConfig("site.adwords.adslot")

  def index(page: Int) = Action {
    Async {
      val cleanPage = if (page >= 0 && page < 10) page else 0
      ScalabitzService.getPublishedArticles(cleanPage).map(list => Ok(views.html.articles(list, cleanPage)))
    }
  }

  def about = Action {
    Ok(views.html.about())
  }

  def getConfig(key: String) = {
    Play.configuration.getString(key).get
  }

}