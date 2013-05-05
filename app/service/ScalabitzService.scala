package service

import play.api.libs.concurrent.Execution.Implicits._
import concurrent.Future
import service.controllers.ArticleRepository
import play.api.libs.json.{JsValue, Json}
import play.Logger
import play.libs.Akka

import play.api.Play
import play.api.Play.current


case class ScalabitzArticle(id: String, article: BitlyArticle, clicks: Int);

/**
 * Responsible for transforming database results to Scalabitz model objects.
 */
object ScalabitzService {

  private[this] lazy val publishTimeout = Play.configuration.getInt("admin.publishtimer").getOrElse(3600)
  private[this] implicit val bitlyArtReads = Json.reads[BitlyArticle]

  def getPublishedArticles(): Future[List[ScalabitzArticle]] = {
    ArticleRepository.getPublishedArticles().map {
      articles =>
        for {
          idAndJson <- articles
          article <- jsToModel(idAndJson)
        } yield article
    }
  }

  def getPendingArticles(): Future[List[ScalabitzArticle]] = {
    ArticleRepository.getPendingArticles().map {
      articles =>
        for {
          idAndJson <- articles
          article <- jsToModel(idAndJson)
        } yield article
    }
  }

  def startScheduledTasks() {
    import akka.actor._
    import scala.concurrent.duration._

    case object Publish

    val publishActor = Akka.system.actorOf(Props(new Actor {
      def receive = {
        case Publish => {
          for {
            articles <- ArticleRepository.getPrepublishedArticles()
            idAndJson <- articles
            id = idAndJson._1
          } {
            Logger.info(s"Published $id")
            ArticleRepository.publishArticle(id)
          }
        }
      }
    }))


    Akka.system.scheduler.schedule(10 seconds, publishTimeout seconds, publishActor, Publish)
  }

  private[this] def jsToModel(idAndJson: (String, JsValue)): Option[ScalabitzArticle] = {
    val article = Json.fromJson[BitlyArticle](idAndJson._2 \ "parsedResults" \ "bitlyArticle")
    article.asOpt.map(article => ScalabitzArticle(idAndJson._1, article, (idAndJson._2 \ "parsedResults" \ "clicks").as[Int]))
  }

}
