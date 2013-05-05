package service

package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api._
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


object ArticleRepository {
  val db = ReactiveMongoPlugin.db
  lazy val collection = db("scalabitz")
  lazy val rawcollection = db("scalabitz_rawresults")

  /**
   * Store raw response. Id is generated and returned so we don't have to wait for the insert.
   *
   * @param rawResult
   * @return generated id
   */
  def storeRawResult(rawResult: JsValue): String = {
    val id = BSONObjectID.generate
    val json = rawResult.as[JsObject] + PlayBsonImplicits.toTuple(DefaultBSONElement("_id", id))
    rawcollection.insert[JsValue](json).foreach(lastError =>
      if (!lastError.ok) Logger.warn(s"Mongo error: $lastError")
    )
    id.stringify
  }

  /**
   * Insert article if it doesn't exist already.
   *
   * @param parsedResults json value to store
   */
  def storeArticle(parsedResults: JsValue) {
    val json = Json.obj(
      "saved_at" -> new java.util.Date().getTime(),
      "parsedResults" -> parsedResults

    )

    articleExists(parsedResults).foreach {
      case false => collection.insert[JsValue](json).foreach(lastError =>
        if (!lastError.ok) Logger.warn(s"Mongo error after insert: $lastError")
      )
      case true => Logger.info("Skipped article");
    }
  }

  def publishArticle(id: String) = {
    val updateCommand = Json.obj("$set" -> Json.obj("parsedResults.isPublished" -> true))
    update(id, updateCommand)
  }

  def prePublishArticle(id: String) = {
    val updateCommand = Json.obj("$set" -> Json.obj("parsedResults.toBePublished" -> "yes"))
    update(id, updateCommand)
  }

  def rejectArticle(id: String) = {
    val updateCommand = Json.obj("$set" -> Json.obj("parsedResults.isPublished" -> false, "parsedResults.toBePublished" -> "never"))
    update(id, updateCommand)
  }

  def updateClicks(id: String, clicks: Int) = {
    val updateCommand = Json.obj("$set" -> Json.obj("parsedResults.clicks" -> clicks))
    update(id, updateCommand)
  }

  def getPublishedArticles(page: Int, howMany: Option[Int] = Some(10)): Future[List[(String, JsValue)]] = {
    buildAndRunQuery(Json.obj("parsedResults.isPublished" -> true), howMany, SortOrder.Descending, page)
  }

  def getPrepublishedArticles(howMany: Option[Int] = Some(1)): Future[List[(String, JsValue)]] = {
    buildAndRunQuery(Json.obj("parsedResults.isPublished" -> false, "parsedResults.toBePublished" -> "yes"), howMany, SortOrder.Ascending)
  }

  def getPendingArticles(): Future[List[(String, JsValue)]] = {
    buildAndRunQuery(Json.obj("parsedResults.isPublished" -> false, "parsedResults.toBePublished" -> "pending"), None, SortOrder.Descending)
  }

  private[this] def getObjId(value: JsValue): String = {
    (value \ "_id" \ "$oid").as[String]
  }

  private[this] def buildAndRunQuery(queryObject: JsValue, maxResults: Option[Int], sortOrder: SortOrder, page: Int = 0): Future[List[(String, JsValue)]] = {
    val qb = QueryBuilder().query(queryObject).sort("saved_at" -> sortOrder)
    val query = collection.find[JsValue](qb, QueryOpts().skip(page*maxResults.getOrElse(0)));
    val list = maxResults match {
      case Some(max) => query.toList(max)
      case None => query.toList();
    }

    list.map(_.map(jsValue => (getObjId(jsValue), jsValue)))
  }

  private[this] def articleExists(parsedResults: JsValue): Future[Boolean] = {
    val hash = parsedResults \ "hash"
    val qb = QueryBuilder().query(Json.obj("parsedResults.hash" -> hash))
    collection.find[JsValue](qb).toList.map(t => !t.isEmpty)
  }

  private[this] def update(id: String, updateCommand: JsObject): Future[Option[String]] = {
    collection.update(BSONDocument("_id" -> BSONObjectID(id)), updateCommand).map(lastError =>
      if (!lastError.ok) {
        Logger.warn(s"Mongo error after update of $id: $lastError")
        Some("Error during update")
      } else None
    )
  }

}
