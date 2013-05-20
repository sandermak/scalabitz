package service

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{WS, Response}
import play.api.libs.json.{JsResult, JsValue, Writes, Json}
import play.api.{Logger, Play}
import play.api.Play.current
import concurrent.Future
import service.controllers.ArticleRepository
import play.api.libs.json.Writes._
import play.libs.Akka
import scala.util.parsing.json.JSONArray


case class BitlyArticle(title: String, url: String, aggregate_link: String, domain: String, content: String, keywords: Option[String]) {
  val programmingTerms = Set("markdown", "ui", "typesafe", "programming", "query", "configuration", "database",
    "websocket", "software", "api", "scaladoc", "akka", "developer", "html5", "framework", "function", "trait")
  var rawResultId: String = _

  // Simply check if any of the programmingTerms occur in any of the relevant fields
  lazy val isProgrammingRelated: Boolean = {
    val fullcontent = List(keywords.map(kw => kw.replace(",", " ")), content, title).mkString(" ");
    fullcontent.toLowerCase.split("\\s+").map(_.replaceAll("[^A-Za-z]", "")).exists(programmingTerms(_))
  }
}

object BitlyService extends Configurable {
  val bitlySearch = "https://api-ssl.bitly.com/v3/search"
  val bitlyClicks = "https://api-ssl.bitly.com/v3/link/clicks"
  private[this] val accesstoken = getConfig("bitly.accesstoken")
  private[this] val articleFetchInterval = getConfigInt("admin.interval.fetcharticles")
  private[this] val clickFetchInterval = getConfigInt("admin.interval.fetchclicks")

  private[this] case class BitlyResponse(status_code: Int, status_txt: String)
  private[this] implicit val bitlyRespReads = Json.reads[BitlyResponse]
  private[this] implicit val bitlyArtReads = Json.reads[BitlyArticle]
  private[this] implicit val bitlyArtWritesExtended = new Writes[BitlyArticle] {
    def writes(a: BitlyArticle): JsValue = {
       val articleJson = Json.toJson[BitlyArticle](a)(Json.writes[BitlyArticle])
       Json.obj(
         "bitlyArticle" -> articleJson,
         "isProgrammingRelated" -> a.isProgrammingRelated,
         "isPublished" -> false,
         "toBePublished" -> "pending",
         "hash" -> a.hashCode.toString,
         "rawResultId" -> a.rawResultId,
         "clicks" -> 0
       )
    }
  }

  def startScheduledTasks() {
    import akka.actor._
    import scala.concurrent.duration._

    case object FetchArticles
    case object FetchClicks

    val fetchClicksActor = Akka.system.actorOf(Props(new Actor {
      def receive = {
        case FetchClicks => {
          val fetchFuture = fetchClicks()
          fetchFuture.onFailure { case exc: Exception => Logger.error("Could not retrieve clicks", exc) }
          fetchFuture.foreach(storeClicks => storeClicks.foreach(entry => ArticleRepository.updateClicks(entry._1, entry._2)));
        }
      }
    }))

    val fetchActor = Akka.system.actorOf(Props(new Actor {
      def receive = {
        case FetchArticles => fetchPossiblyNewArticles().onFailure { case exc: Exception => Logger.error("Could not retrieve articles", exc) }
      }
    }))

    Akka.system.scheduler.schedule(10 seconds, articleFetchInterval minutes, fetchActor, FetchArticles)
    Akka.system.scheduler.schedule(10 seconds, clickFetchInterval minutes, fetchClicksActor, FetchClicks)
  }

  def fetchPossiblyNewArticles(): Future[List[BitlyArticle]] = {
    val url = s"$bitlySearch?query=scala&access_token=$accesstoken"
    Logger.info(s"Fetching (possibly) new articles from Bit.ly url $url");

    WS.url(url).get().map { r =>
       Logger.info(s"Bit.ly responded with status ${r.status} for call $url");
       val bitlyArticles = for {
         result <- parseResult(r) if result.status_code == 200
       } yield parseArticles(r)

       // Store bit.ly response and the individual articles (only new ones are stored, based on hash of article)
       val articleList = bitlyArticles.getOrElse(List())
       if(!articleList.isEmpty) {
         Logger.info(s"Storing raw result for call $url")
         val rawId = ArticleRepository.storeRawResult(r.json)
         Logger.info(s"Storing individual parsed articles for call $url")
         for (article <- articleList) {
           article.rawResultId = rawId
           ArticleRepository.storeArticle(Json.toJson[BitlyArticle](article))
         }
       } else {
         Logger.warn(s"No articles could be parsed for call $url")
       }

       articleList
    }
  }

  def fetchClicks(): Future[List[(String,Int)]] = {
    def clickWSCall(article: ScalabitzArticle) = {
      val bitlylink = article.article.aggregate_link;
      val url = s"$bitlyClicks?unit=month&rollup=true&link=$bitlylink&access_token=$accesstoken"

      WS.url(url).get().map { r =>
        Logger.info(s"Bit.ly responded with status ${r.status} for call $url");
        val clicks = r.json \ "data" \ "link_clicks"
        Logger.info(s"Response indicates $clicks clicks for ${article.id}")
        (article.id, clicks.as[Int])
      }
    }

    // Retrieve clicks for the last 100 published articles
    ScalabitzService.getPublishedArticles(0, 100).flatMap { articles =>
      val clickFutures = for(article <- articles) yield clickWSCall(article)
      Future.sequence(clickFutures)
    }
  }

  private[this] def parseResult(r: Response): Option[BitlyResponse] = {
    val result: JsResult[BitlyResponse] = Json.fromJson[BitlyResponse](r.json)
    val resultOpt = result.asOpt
    if(!resultOpt.isDefined) Logger.error(s"JSON parsing failed for complete response: $result")
    resultOpt
  }

  private[this] def parseArticles(r: Response): List[BitlyArticle] = {
    val result = collection.mutable.ListBuffer[BitlyArticle]()
    for(value <- (r.json \ "data" \ "results").as[List[JsValue]]) {
       Json.fromJson[BitlyArticle](value).fold(errors => Logger.error(s"JSON parsing failed for individual article: $errors"), article => result += article)
    }

    result.toList
  }
}
