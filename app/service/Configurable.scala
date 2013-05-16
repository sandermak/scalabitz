package service

import play.api.Play
import play.api.Play.current

trait Configurable {
  def getConfig(key: String) = {
    Play.configuration.getString(key).get
  }
}
