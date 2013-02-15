package service

import play.api.libs.concurrent.Execution.Implicits._
import concurrent.Future
import service.controllers.ArticleRepository
import play.api.libs.json.{JsValue, Json}

object ScalabitzService {
  private[this] implicit val bitlyArtReads = Json.reads[BitlyArticle]

  //  ArticleStorageService.getPublishedArticles().map { jsonList =>
  //    for {
  //      json <- jsonList
  //      result <- Json.fromJson[BitlyArticle](json \ "parsedResults" \ "bitlyArticle").asOpt
  //    } yield result
  //
  //  }
  def getPublishedArticles(): Future[List[BitlyArticle]] = {
    ArticleRepository.getPublishedArticles().map {
      articles =>
        for {
          articleJs <- articles
          article <- jsToModel(articleJs)
        } yield article
    }
  }

  def getAllArticles(): Future[List[(String,BitlyArticle)]] = {
    ArticleRepository.getAllArticles().map {
      articles =>
        for {
          articleJs <- articles
          article <- jsToModel(articleJs._2)
        } yield (articleJs._1, article)
      //ArticleRepository.getAllArticles().map(((t: List[(String, BitlyArticle)]) => t.map { (id: String,value: JsValue) => (id, jsToModel(value))})
    }
  }

  private[this] def jsToModel(json: JsValue): Option[BitlyArticle] = {
    Json.fromJson[BitlyArticle](json \ "parsedResults" \ "bitlyArticle").asOpt
  }

}
