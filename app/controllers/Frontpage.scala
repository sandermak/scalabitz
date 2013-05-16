package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import service.{Configurable, ScalabitzService}


object Frontpage extends Controller with Configurable {

  // Google Analytics properties, used in mainlayout.scala.html
  lazy val domain = getConfig("site.ga.domain")
  lazy val trackingid = getConfig("site.ga.trackingid")
  lazy val adclient = getConfig("site.adwords.adclient")
  lazy val adslot = getConfig("site.adwords.adslot")

  def articles(page: Int) = Action {
    Async {
      val cleanPage = if (page >= 0 && page < 10) page else 0
      ScalabitzService.getPublishedArticles(cleanPage).map(list => Ok(views.html.articles(list, cleanPage)))
    }
  }

  def about = Action {
    Ok(views.html.about())
  }



}