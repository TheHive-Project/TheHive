package controllers

import javax.inject.{ Inject, Singleton }

import play.api.Environment
import play.api.http.HttpErrorHandler
import play.api.mvc.{ Action, AnyContent, Controller }

trait AssetCtrl {
  def get(file: String): Action[AnyContent]
}

@Singleton
class AssetCtrlProd @Inject() (errorHandler: HttpErrorHandler) extends Assets(errorHandler) with AssetCtrl {
  def get(file: String) = at("/ui", file)
}

@Singleton
class AssetCtrlDev @Inject() (environment: Environment) extends ExternalAssets(environment) with AssetCtrl {
  def get(file: String) = {
    if (file.startsWith("bower_components/")) {
      at("ui", file)
    }
    else {
      at("ui/app", file)
    }
  }
}