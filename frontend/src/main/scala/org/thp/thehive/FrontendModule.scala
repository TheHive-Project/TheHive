package org.thp.thehive

import controllers.ExternalAssets
import org.thp.scalligraph.{ErrorHandler, ScalligraphApplication, ScalligraphModule}
import play.api.Mode
import play.api.http.HttpErrorHandler
import play.api.mvc.Results
import play.api.routing.SimpleRouter
import play.api.routing.sird._

class FrontendModule(app: ScalligraphApplication) extends ScalligraphModule {
  import app._
  import app.context.environment

  lazy val extAssets: ExternalAssets = new ExternalAssets(environment)(executionContext, fileMimeTypes)
  val errorHandler: HttpErrorHandler = ErrorHandler
  routers += (environment.mode match {
    case Mode.Prod =>
      SimpleRouter {
        case GET(p"/") =>
          app.defaultActionBuilder(Results.PermanentRedirect(configuration.get[String]("play.http.context").stripSuffix("/") + "/index.html"))
        case GET(p"/$file*") if !file.startsWith("api/") => assets.at("/community/frontend", file)
      }
    case _ =>
      val frontendPath = app.configuration.getOptional[String]("frontend.path").getOrElse("frontend")
      SimpleRouter {
        case GET(p"/") =>
          app.defaultActionBuilder(Results.PermanentRedirect(configuration.get[String]("play.http.context").stripSuffix("/") + "/index.html"))
        case GET(p"/$file*") if !file.startsWith("api/") && file.startsWith("bower_components") => extAssets.at(frontendPath, file)
        case GET(p"/$file*") if !file.startsWith("api/")                                        => extAssets.at(s"$frontendPath/app", file)
      }
  })
}
