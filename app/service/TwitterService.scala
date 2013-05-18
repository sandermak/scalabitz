package service

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.oauth.{OAuthCalculator, RequestToken, ConsumerKey}
import play.api.libs.ws.WS
import play.api.Logger
import java.net.URLEncoder
import scala.concurrent.Future


object TwitterService extends Configurable {
  val shortenedUrlLength = 23 // TODO: get this from the Twitter config API endpoint, may change over time

  val postTweetUrl = "https://api.twitter.com/1.1/statuses/update.json?status="
  val consumerKey = ConsumerKey(getConfig("twitter.consumer.key"), getConfig("twitter.consumer.secret"))
  val accessToken = RequestToken(getConfig("twitter.accessToken.key"), getConfig("twitter.accessToken.secret"))

  def postTweet(sbArticle: ScalabitzArticle): Future[Option[String]] = {
    Logger.info(s"Posting tweet for ${sbArticle.id}")
    val msg = createMsg(sbArticle)
    postMsg(msg)
  }

  private[this] def createMsg(sbArticle: ScalabitzArticle): String = {
    val maxLength = 139 - shortenedUrlLength // reserve char for space between title and url

    def truncate(s: String) = {
      val maxString = s.take(maxLength)
      val lastSpace = maxString.lastIndexOf(" ")
      if(lastSpace != -1 && lastSpace < maxString.length) maxString.substring(0, lastSpace) + ".." else maxString.trim
    }

    val title = sbArticle.article.title
    val titlePart = if(title.length < maxLength) title else truncate(title)
    s"$titlePart ${sbArticle.article.url}"
  }

  private[this] def postMsg(msg: String): Future[Option[String]]  = {
    val encodedMsg = URLEncoder.encode(msg, "UTF-8")
    WS.url(s"$postTweetUrl$encodedMsg")
      .sign(OAuthCalculator(consumerKey, accessToken))
      .post("").map { response =>
          Logger.info(s"Posted tweet. Status: ${response.status} -> ${response.statusText}")
          if(response.status == 200) None else Some(response.statusText)
      }
  }

}
