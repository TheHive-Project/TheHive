package org.thp.thehive.controllers.v0

import com.typesafe.config.{Config, ConfigRenderOptions}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.{AuthorizationError, NotFoundError}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{OrganisationConfigContext, UserConfigContext}
import play.api.libs.json.{JsNull, JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent, Results}
import play.api.{ConfigLoader, Logger}

import scala.util.{Failure, Success, Try}

@Singleton
class ConfigCtrl @Inject() (
    appConfig: ApplicationConfig,
    userConfigContext: UserConfigContext,
    organisationConfigContext: OrganisationConfigContext,
    entrypoint: Entrypoint
) {

  lazy val logger: Logger = Logger(getClass)

  implicit val configWrites: Writes[ConfigItem[_, _]] = Writes[ConfigItem[_, _]](item =>
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
    entrypoint("list configuration items")
      .authPermitted(Permissions.manageConfig) { request =>
        if (request.organisation != "admin")
          Failure(AuthorizationError("You must be in `admin` organisation to view global configuration"))
        else
          Success(Results.Ok(Json.toJson(appConfig.list)))
      }

  def set(path: String): Action[AnyContent] =
    entrypoint("set configuration item")
      .extract("value", FieldsParser.json.on("value"))
      .authPermitted(Permissions.manageConfig) { request =>
        if (request.organisation != "admin")
          Failure(AuthorizationError("You must be in `admin` organisation to change global configuration"))
        else {
          logger.info(s"app config value set: $path ${request.body("value")}")
          appConfig.set(path, request.body("value"))(request).map(_ => Results.NoContent)
        }
      }

  def get(path: String): Action[AnyContent] =
    entrypoint("get configuration item")
      .authPermitted(Permissions.manageConfig) { request =>
        if (request.organisation != "admin")
          Failure(AuthorizationError("You must be in `admin` organisation to change global configuration"))
        else
          appConfig.get(path) match {
            case Some(c) => Success(Results.Ok(configWrites.writes(c)))
            case None    => Failure(NotFoundError(s"Configuration item $path not found"))
          }
      }

  def userSet(path: String): Action[AnyContent] =
    entrypoint("set user configuration item")
      .extract("value", FieldsParser.json.on("value"))
      .auth { implicit request =>
        val config = appConfig.context(userConfigContext).item[JsValue](path, "")
        logger.info(s"user config value set: $path ${request.body("value")}")
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
    entrypoint("get user configuration item")
      .auth { implicit request =>
        Try {
          val config = appConfig.context(userConfigContext).item[JsValue](path, "")
          Results.Ok(
            Json.obj(
              "path"         -> config.path,
              "defaultValue" -> config.getDefaultValueJson,
              "value"        -> config.getJson(request)
            )
          )
        }.recover {
          case _ =>
            Results.Ok(
              Json.obj(
                "path"         -> path,
                "defaultValue" -> JsNull,
                "value"        -> JsNull
              )
            )
        }
      }

  def organisationGet(path: String): Action[AnyContent] =
    entrypoint("get organisation configuration item")
      .auth { implicit request =>
        Try {
          val config = appConfig.context(organisationConfigContext).item[JsValue](path, "")
          Results.Ok(
            Json.obj(
              "path"         -> config.path,
              "defaultValue" -> config.getDefaultValueJson,
              "value"        -> config.getJson(request)
            )
          )
        }.recover {
          case _ =>
            Results.Ok(
              Json.obj(
                "path"         -> path,
                "defaultValue" -> JsNull,
                "value"        -> JsNull
              )
            )
        }
      }

  def organisationSet(path: String): Action[AnyContent] =
    entrypoint("set organisation configuration item")
      .extract("value", FieldsParser.json.on("value"))
      .authPermitted(Permissions.manageConfig) { implicit request =>
        val config = appConfig.context(organisationConfigContext).item[JsValue](path, "")
        logger.info(s"organisation config value set: $path ${request.body("value")}")
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
}
