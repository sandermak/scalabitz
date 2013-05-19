package service

import play.api.Play

trait Configurable {
  import play.api.Play.current

  def getConfig(key: String) = {
    Play.configuration.getString(key).get
  }

  def getConfigInt(key: String) = {
    Play.configuration.getInt(key).get
  }
}
