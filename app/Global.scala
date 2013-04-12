import play.api._
import service.BitlyArticleRetrievalService

object Global extends GlobalSettings {


  override def onStart(app: Application) {

    BitlyArticleRetrievalService.startScheduledRetrieval()

  }

}
