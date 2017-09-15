package controllers

import javax.inject.{ Inject, Singleton }

import scala.concurrent.ExecutionContext

import play.api.Environment
import play.api.http.{ FileMimeTypes, HttpErrorHandler }
import play.api.mvc.{ Action, AnyContent }

trait AssetCtrl {
  def get(file: String): Action[AnyContent]
}

@Singleton
class AssetCtrlProd @Inject() (errorHandler: HttpErrorHandler, meta: AssetsMetadata) extends Assets(errorHandler, meta) with AssetCtrl {
  def get(file: String): Action[AnyContent] = at("/ui", file)
}

@Singleton
class AssetCtrlDev @Inject() (environment: Environment)(implicit ec: ExecutionContext, fileMimeTypes: FileMimeTypes) extends ExternalAssets(environment) with AssetCtrl {
  def get(file: String): Action[AnyContent] = {
    if (file.startsWith("bower_components/")) {
      at("ui", file)
    }
    else {
      at("ui/app", file)
    }
  }
}