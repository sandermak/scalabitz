package service

import play.api.libs.concurrent.Execution.Implicits._
import concurrent.Future
import service.controllers.ArticleRepository
import play.api.libs.json.{JsValue, Json}
import play.Logger
import play.libs.Akka

case class ScalabitzArticle(id: String, article: BitlyArticle, clicks: Int, alreadySeen: Option[Boolean] = Some(false));

/**
 * Responsible for transforming database results to Scalabitz model objects.
 */
object ScalabitzService extends Configurable {

  // Actor and its messsages
  lazy val publishActor = createPublishActor()
  case class PublishArticle(triesLeft: Int = 3)
  case class PublishTweet(article: ScalabitzArticle, triesLeft: Int = 3)

  private[this] lazy val publishTimeout = getConfigInt("admin.interval.publish")
  private[this] implicit val bitlyArtReads = Json.reads[BitlyArticle]

  def getPublishedArticles(page: Int, howMany: Int = 10): Future[List[ScalabitzArticle]] = {
    ArticleRepository.getPublishedArticles(page, howMany).map(jsListToModelList)
  }

  def getPendingArticles(): Future[List[ScalabitzArticle]] = {
    ArticleRepository.getPendingArticles().map(jsListToModelListWithSeen)
  }

  def publishNow() = {
    publishActor ! PublishArticle()
  }

  def createPublishActor() = {
    import akka.actor._
    Akka.system.actorOf(Props(new Actor {
      def receive = {
        case PublishArticle(count) if count > 0 => {
          for {
            articles <- ArticleRepository.getPrepublishedArticles(1)
            idAndJson <- articles
            article <- jsToModel(idAndJson._1, idAndJson._2)
            id = idAndJson._1
          } {
            val publishFuture = ArticleRepository.publishArticle(id)
            publishFuture.onSuccess { case _ =>
              Logger.info(s"Published $id")
              self ! PublishTweet(article)
            }
            publishFuture.onFailure { case exc =>
              Logger.info(s"Republishing article ${article.id}, ${count - 1} retries left")
              self ! PublishArticle(count - 1)
            }
          }
        }

        case PublishArticle(0) => Logger.warn(s"Publishing article failed, won't retry")

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

  }
  def startScheduledTasks() {
    import scala.concurrent.duration._

    Akka.system.scheduler.schedule(10 seconds, publishTimeout minutes, publishActor, PublishArticle())
  }

  private[this] def jsListToModelListWithSeen(articles: List[(String, JsValue, Boolean)]): List[ScalabitzArticle] = {
      for {
        idAndJson <- articles
        article <- jsToModel(idAndJson._1, idAndJson._2, Some(idAndJson._3))
      } yield article
  }

  private[this] def jsListToModelList(articles: List[(String, JsValue)]): List[ScalabitzArticle] = {
    for {
      idAndJson <- articles
      article <- jsToModel(idAndJson._1, idAndJson._2)
    } yield article
  }

  private[this] def jsToModel(id: String, json: JsValue, seen: Option[Boolean] = None): Option[ScalabitzArticle] = {
    val article = Json.fromJson[BitlyArticle](json \ "parsedResults" \ "bitlyArticle")
    val clicks = (json \ "parsedResults" \ "clicks").as[Int]
    article.asOpt.map(article => ScalabitzArticle(id, article, clicks, seen))
  }

}
