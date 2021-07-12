package org.thp.thehive.controllers.dav

import org.thp.scalligraph.{ScalligraphApplication, ScalligraphModule}
import org.thp.thehive.TheHiveModule

class TheHiveFSModule(app: ScalligraphApplication) extends ScalligraphModule {

  import com.softwaremill.macwire._

  lazy val theHiveModule: TheHiveModule = app.getModule[TheHiveModule]
  lazy val vfs: VFS                     = wire[VFS]
  app.routers += wire[Router].withPrefix("/fs")
}
