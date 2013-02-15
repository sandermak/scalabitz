package service

package controllers

import play.api._
import play.api.mvc._
import scala.concurrent.Future

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._

// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._

// Play Json imports
import play.api.libs.json._

import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global

object ArticleStorageService {
  val db = ReactiveMongoPlugin.db
  lazy val collection = db("scalabitz")
  lazy val rawcollection = db("scalabitz_rawresults")

  def storeRawResult(rawResult: JsValue): String = {
    val id = BSONObjectID.generate
    val json = rawResult.as[JsObject] + PlayBsonImplicits.toTuple(DefaultBSONElement("_id", id))
    rawcollection.insert[JsValue](json).foreach(lastError =>
      if(!lastError.ok) Logger.warn(s"Mongo error: $lastError")
    )
    id.value.toString
  }

  def storeArticle(parsedResults: JsValue) {
    val json = Json.obj(
      "saved_at" -> new java.util.Date().getTime(),
      "parsedResults" -> parsedResults

    )
    articleExists(parsedResults).foreach {
      case false => collection.insert[JsValue](json).foreach(lastError =>
        if(!lastError.ok) Logger.warn(s"Mongo error after insert: $lastError")
      )
      case true => Logger.debug("Skipped article")
    }
  }

  def getPublishedArticles(): Future[List[JsValue]] = {
    val qb = QueryBuilder().query(Json.obj( "parsedResults.isPublished" -> true))
    collection.find[JsValue](qb).toList
  }

  private[this] def articleExists(parsedResults: JsValue): Future[Boolean] = {
    val hash = parsedResults \ "hash"
    val qb = QueryBuilder().query(Json.obj( "parsedResults.hash" -> hash))
    collection.find[JsValue](qb).toList.map(t => !t.isEmpty)
  }

}