import play.api._
import service.{TwitterService, ScalabitzService, BitlyService}

object Global extends GlobalSettings {


  override def onStart(app: Application) {
    BitlyService.startScheduledTasks()
    ScalabitzService.startScheduledTasks()
  }

}
