package org.thp.thehive.controllers.v0

import scala.util.{Failure, Success, Try}

import play.api.ConfigLoader
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent, Results}

import com.typesafe.config.{Config, ConfigRenderOptions}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.AuthorizationError
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{ConfigSrv, UserConfigContext}

@Singleton
class ConfigCtrl @Inject()(
    appConfig: ApplicationConfig,
    configSrv: ConfigSrv,
    userConfigContext: UserConfigContext,
    entryPoint: EntryPoint,
    db: Database
) {

  implicit val configWrites: Writes[ConfigItem[_, _]] = Writes[ConfigItem[_, _]](
    item =>
      Json.obj(
        "path"         -> item.path,
        "description"  -> item.description,
        "defaultValue" -> item.getDefaultValueJson,
        "value"        -> item.getJson
      )
  )

  implicit val jsonConfigLoader: ConfigLoader[JsValue] = (config: Config, path: String) =>
    Json.parse(config.getValue(path).render(ConfigRenderOptions.concise()))

  def list: Action[AnyContent] =
    entryPoint("list configuration items")
      .auth {
        case request if request.permissions.contains(Permissions.manageConfig) =>
          Success(Results.Ok(Json.toJson(appConfig.list)))
        case _ => Failure(AuthorizationError("You need manageConfig permission to view configuration"))
      }

  def set(path: String): Action[AnyContent] =
    entryPoint("set configuration item")
      .extract("value", FieldsParser.json)
      .auth {
        case request if request.permissions.contains(Permissions.manageConfig) =>
          appConfig.set(path, request.body("value"))(request).map(_ => Results.NoContent)
        case _ => Failure(AuthorizationError("You need manageConfig permission to set configuration"))
      }

  def userSet(path: String): Action[AnyContent] =
    entryPoint("set user configuration item")
      .extract("value", FieldsParser.json.on("value"))
      .authTransaction(db) { implicit request => implicit graph =>
        val config = appConfig.context(userConfigContext).item[JsValue](path, "")
        config.setJson(request, request.body("value")).map { _ =>
          Results.Ok(
            Json.obj(
              "path"         -> config.path,
              "defaultValue" -> config.getDefaultValueJson,
              "value"        -> config.getJson(request)
            )
          )
        }
      }

  def userGet(path: String): Action[AnyContent] =
    entryPoint("get user configuration item")
      .authTransaction(db) { implicit request => implicit graph =>
        Try {
          val config = appConfig.context(userConfigContext).item[JsValue](path, "")
          Results.Ok(
            Json.obj(
              "path"         -> config.path,
              "defaultValue" -> config.getDefaultValueJson,
              "value"        -> config.getJson(request)
            )
          )
        }
      }
}
