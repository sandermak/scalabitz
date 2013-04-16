package service

import play.api.libs.concurrent.Execution.Implicits._
import concurrent.Future
import service.controllers.ArticleRepository
import play.api.libs.json.{JsValue, Json}
import play.Logger


case class ScalabitzArticle(id: String, article: BitlyArticle, clicks: Int);

object ScalabitzService {
  private[this] implicit val bitlyArtReads = Json.reads[BitlyArticle]

  def getPublishedArticles(): Future[List[ScalabitzArticle]] = {
    ArticleRepository.getPublishedArticles().map {
      articles =>
        for {
          articleJs <- articles
          article <- jsToModel(articleJs)
        } yield article
    }
  }

  def getAllArticles(): Future[List[ScalabitzArticle]] = {
    ArticleRepository.getAllArticles().map {
      articles =>
        for {
          articleJs <- articles
          article <- jsToModel(articleJs)
        } yield article
    }
  }

  private[this] def jsToModel(json: (String, JsValue)): Option[ScalabitzArticle] = {
    val article = Json.fromJson[BitlyArticle](json._2 \ "parsedResults" \ "bitlyArticle")
    article.asOpt.map(article => ScalabitzArticle(json._1, article, (json._2 \ "parsedResults" \ "clicks").as[Int]))
  }

}
