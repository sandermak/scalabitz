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

  def getPublishedArticles(page: Int, howMany: Int = 10): Future[List[ScalabitzArticle]] = {
    ArticleRepository.getPublishedArticles(page, howMany).map(jsListToModelList)
  }

  def getPendingArticles(): Future[List[ScalabitzArticle]] = {
    ArticleRepository.getPendingArticles().map(jsListToModelList)
  }

  def startScheduledTasks() {
    import akka.actor._
    import scala.concurrent.duration._

    case object PublishArticle
    case class PublishTweet(article: ScalabitzArticle, triesLeft: Int)

    val publishActor = Akka.system.actorOf(Props(new Actor {
      def receive = {
        case PublishArticle => {
          for {
            articles <- ArticleRepository.getPrepublishedArticles(1)
            idAndJson <- articles
            article <- jsToModel(idAndJson)
            id = idAndJson._1
          } {
            ArticleRepository.publishArticle(id)
            Logger.info(s"Published $id")
            self ! PublishTweet(article, 3)
          }
        }

        case PublishTweet(article, count) if count > 0 => {
          TwitterService.postTweet(article).map(twitterMsg =>
            twitterMsg.foreach { error =>
              Logger.info(s"Reposting tweet for ${article.id} failed, ${count - 1} retries left")
              self ! PublishTweet(article, count - 1) }
          )
        }

        case PublishTweet(article, 0) => Logger.warn(s"Reposting tweet for ${article.id} failed, won't retry")
      }
    }))


    Akka.system.scheduler.schedule(10 seconds, publishTimeout seconds, publishActor, PublishArticle)
  }

  private[this] def jsListToModelList(articles: List[(String, JsValue)]): List[ScalabitzArticle] = {
      for {
        idAndJson <- articles
        article <- jsToModel(idAndJson)
      } yield article
  }

  private[this] def jsToModel(idAndJson: (String, JsValue)): Option[ScalabitzArticle] = {
    val article = Json.fromJson[BitlyArticle](idAndJson._2 \ "parsedResults" \ "bitlyArticle")
    article.asOpt.map(article => ScalabitzArticle(idAndJson._1, article, (idAndJson._2 \ "parsedResults" \ "clicks").as[Int]))
  }

}
