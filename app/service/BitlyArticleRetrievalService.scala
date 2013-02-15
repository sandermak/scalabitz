package service

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{WS, Response}
import play.api.libs.json.{JsValue, Writes, Json}
import play.api.{Logger, Play}
import play.api.Play.current
import concurrent.Future
import service.controllers.ArticleRepository
import play.api.libs.json.Writes._



case class BitlyArticle(title: String, url: String, domain: String, content: String, keywords: Option[String]) {
  val programmingTerms = Set("markdown", "ui", "typesafe", "programming", "query", "configuration", "database",
    "websocket", "development", "api", "scaladoc", "akka", "developer", "html5", "framework", "function")
  var rawResultId: String = _

  // Simply check if the programmingTerms occur in any of the relevant fields
  lazy val isProgrammingRelated: Boolean = {
    val fullcontent = List(keywords.map(kw => kw.replace(",", " ")), content, title).mkString(" ");
    fullcontent.toLowerCase.split("\\s+").map(_.replaceAll("[^A-Za-z]", "")).exists(programmingTerms(_))
  }
}

object BitlyArticleRetrievalService {
  val bitlySearch = "https://api-ssl.bitly.com/v3/search"
  private[this] val accesstoken = Play.configuration.getString("bitly.accesstoken").get

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
         "hash" -> a.hashCode.toString,
         "rawResultId" -> a.rawResultId
       )
    }
  }

  def fetchPossiblyNewArticles(): Future[List[BitlyArticle]] = {
    WS.url(s"$bitlySearch?query=scala&access_token=$accesstoken" ).get().map { r =>
       Logger.info("Bit.ly responded with status " + r.status)
       val bitlyArticles = for {
         result <- parseResult(r) if result.status_code == 200
         articles <- parseArticles(r)
       } yield articles

       // Store bit.ly response and the individual articles (only new ones are stored, based on hash of article)
       val articleList = bitlyArticles.getOrElse(List())
       val rawId = ArticleRepository.storeRawResult(r.json)
       for (article <- articleList) {
         article.rawResultId = rawId
         ArticleRepository.storeArticle(Json.toJson[BitlyArticle](article))
       }

       articleList
    }
  }

  private[this] def parseResult(r: Response): Option[BitlyResponse] = {
    Json.fromJson[BitlyResponse](r.json).asOpt
  }

  private[this] def parseArticles(r: Response): Option[List[BitlyArticle]] = {
    Json.fromJson[List[BitlyArticle]](r.json \ "data" \ "results").asOpt
  }
}
