package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import libs.json.Json
import play.api.mvc._
import service.BitlyArticle
import service.controllers.ArticleStorageService


object Application extends Controller {

  def index = Action {
    Async {
      implicit val bitlyArtReads = Json.reads[BitlyArticle]
      ArticleStorageService.getPublishedArticles().map { jsonList =>
        for {
          json <- jsonList
          result <- Json.fromJson[BitlyArticle](json \ "parsedResults" \ "bitlyArticle").asOpt
        } yield result

      }.map(list => Ok(views.html.index(list)))

    }
  }

}