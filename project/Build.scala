import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "scalabitz"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "org.reactivemongo" %% "play2-reactivemongo" % "0.8"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers += "sgodbillon" at "https://bitbucket.org/sgodbillon/repository/raw/master/snapshots/"
  )

}
