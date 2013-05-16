package service

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.oauth.{OAuthCalculator, RequestToken, ConsumerKey}
import play.api.libs.ws.WS
import play.api.Logger
import java.net.URLEncoder


object TwitterService extends Configurable {
  val postTweetUrl = "https://api.twitter.com/1.1/statuses/update.json?status="
  val consumerKey = ConsumerKey(getConfig("twitter.consumer.key"), getConfig("twitter.consumer.secret"))
  val accessToken = RequestToken(getConfig("twitter.accessToken.key"), getConfig("twitter.accessToken.secret"))

  def postTweet(article: ScalabitzArticle) = {
    Logger.info("Posting tweet")
    val msg = URLEncoder.encode("Testing Twitter integration for @Scalabitz. Plz ignore :)", "UTF-8")
    WS.url(s"$postTweetUrl$msg")
      .sign(OAuthCalculator(consumerKey, accessToken))
      .post("").map(response => Logger.info(s"Status: ${response.status} \n Response: ${response.body}"))
  }

}
