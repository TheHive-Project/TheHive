import sbt._
import sbt.Keys._
import scala.sys.process.Process

import Path.rebase

object FrontEnd extends AutoPlugin {

  object autoImport {
    val frontendDev = taskKey[Unit]("Build front-end in dev")
    val frontendFiles = taskKey[Seq[(File, String)]]("Front-end files")
  }

  import autoImport._

  override def trigger = allRequirements

  override def projectSettings = Seq[Setting[_]](
    frontendDev := {
      val s = streams.value
      s.log.info("Preparing front-end for dev (grunt wiredep)")
      Process("grunt" :: "wiredep" :: Nil, baseDirectory.value / "ui") ! s.log
      ()
    },

    frontendFiles := {
      val s = streams.value
      val ext = if (System.getProperty("os.name").toLowerCase().contains("windows")) ".cmd" else ""

      s.log.info("Preparing front-end for prod ...")
      s.log.info(s"npm$ext install")
      Process(s"npm$ext" :: "install" :: Nil, baseDirectory.value / "ui") ! s.log
      s.log.info(s"bower$ext install")
      Process(s"bower$ext" :: "install" :: Nil, baseDirectory.value / "ui") ! s.log
      s.log.info(s"grunt$ext build")
      Process(s"grunt$ext" :: "build" :: Nil, baseDirectory.value / "ui") ! s.log
      val dir = baseDirectory.value / "ui" / "dist"
      (dir.**(AllPassFilter)) pair rebase(dir, "ui")
    })
}