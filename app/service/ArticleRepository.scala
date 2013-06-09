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

    articleExistsForQuery(Json.obj("parsedResults.hash" -> parsedResults \ "hash")).foreach {
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

  def removeArticle(id: String) = {
    collection.remove(BSONDocument("_id" -> BSONObjectID(id))).map(lastError =>
      if (!lastError.ok) {
        Logger.warn(s"Mongo error after update of $id: $lastError")
        Some(s"Error during removal of $id")
      } else None
    )
  }

  def updateClicks(id: String, clicks: Int) = {
    val updateCommand = Json.obj("$set" -> Json.obj("parsedResults.clicks" -> clicks))
    update(id, updateCommand)
  }

  def getPublishedArticles(page: Int, howMany: Int): Future[List[(String, JsValue)]] = {
    buildAndRunQuery(Json.obj("parsedResults.isPublished" -> true), Some(howMany), SortOrder.Descending, page)
  }

  def getPrepublishedArticles(howMany: Int): Future[List[(String, JsValue)]] = {
    buildAndRunQuery(Json.obj("parsedResults.isPublished" -> false, "parsedResults.toBePublished" -> "yes"), Some(howMany), SortOrder.Ascending)
  }

  def getPendingArticles(): Future[List[(String, JsValue, Boolean)]] = {
    buildAndRunQuery(Json.obj("parsedResults.isPublished" -> false, "parsedResults.toBePublished" -> "pending"), None, SortOrder.Descending).flatMap { articles =>
      // Find out if we've seen these articles before, add this info to the articles result.
      val existsFutures = articles.map {
        article => articleExistsForQuery(Json.obj("parsedResults.bitlyArticle.url" -> article._2 \ "parsedResults" \ "bitlyArticle" \ "url"), 1)
      }
      Future.sequence(existsFutures).map(existsResults => (articles, existsResults).zipped.map((article, exists) => (article._1, article._2, exists)))
    }
  }

  private[this] def getObjId(value: JsValue): String = {
    (value \ "_id" \ "$oid").as[String]
  }

  private[this] def buildAndRunQuery(queryObject: JsValue, maxResults: Option[Int], sortOrder: SortOrder, page: Int = 0): Future[List[(String, JsValue)]] = {
    val qb = QueryBuilder().query(queryObject).sort("saved_at" -> sortOrder)
    val query = collection.find[JsValue](qb, QueryOpts().skip(page*maxResults.getOrElse(0)));
    val list = maxResults match {
      case Some(max) => query.toList(max)
      case None => query.toList()
    }

    list.map(_.map(jsValue => (getObjId(jsValue), jsValue)))
  }

  private[this] def articleExistsForQuery(queryObject: JsValue, maxExpected: Int = 0): Future[Boolean] = {
    val qb = QueryBuilder().query(queryObject)
    collection.find[JsValue](qb).toList.map(_.size > maxExpected)
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
