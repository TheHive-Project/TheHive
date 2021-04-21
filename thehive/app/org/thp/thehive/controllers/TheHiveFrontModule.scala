package org.thp.thehive.controllers

import controllers.ExternalAssets
import org.thp.scalligraph.{ErrorHandler, ScalligraphApplication, ScalligraphModule}
import play.api.Mode
import play.api.http.HttpErrorHandler
import play.api.routing.SimpleRouter
import play.api.routing.sird._

class TheHiveFrontModule(app: ScalligraphApplication) extends ScalligraphModule {
  import app._
  import app.context.environment

  lazy val extAssets: ExternalAssets = new ExternalAssets(environment)(executionContext, fileMimeTypes)
  val errorHandler: HttpErrorHandler = ErrorHandler
  routers += (environment.mode match {
    case Mode.Prod =>
      SimpleRouter {
        case GET(p"/$file*") if !file.startsWith("api/") => assets.at(file)
      }
    case _ =>
      SimpleRouter {
        case GET(p"/$file*") if !file.startsWith("api/") && file.startsWith("bower_components") => extAssets.at("frontend", file)
        case GET(p"/$file*") if !file.startsWith("api/")                                        => extAssets.at("frontend/app", file)
      }
  })
}
