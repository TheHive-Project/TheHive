package org.thp.thehive.controllers.dav

import org.thp.scalligraph.{ScalligraphApplication, ScalligraphModule}
import org.thp.thehive.TheHiveModule

class TheHiveFSModule(app: ScalligraphApplication, theHiveModule: TheHiveModule) extends ScalligraphModule {
  def this(app: ScalligraphApplication) = this(app, app.getModule[TheHiveModule])

  import com.softwaremill.macwire._

  lazy val vfs: VFS = wire[VFS]
  app.routers += wire[Router].withPrefix("/fs")
}
