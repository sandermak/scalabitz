import play.api._
import service.BitlyService

object Global extends GlobalSettings {


  override def onStart(app: Application) {

    BitlyService.startScheduledTasks()

  }

}
