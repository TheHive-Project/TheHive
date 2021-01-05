package org.thp.thehive.controllers.v0

import com.typesafe.config.{ConfigRenderOptions, Config => TypeSafeConfig}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.{ApplicationConfig, ConfigItem}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{AuthorizationError, NotFoundError}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, Result, Results}
import play.api.{ConfigLoader, Configuration, Logger}

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class ConfigCtrl @Inject() (
    configuration: Configuration,
    appConfig: ApplicationConfig,
    userConfigContext: UserConfigContext,
    organisationConfigContext: OrganisationConfigContext,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    entrypoint: Entrypoint,
    db: Database
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

  implicit val jsonConfigLoader: ConfigLoader[JsValue] = (config: TypeSafeConfig, path: String) =>
    Json.parse(config.getValue(path).render(ConfigRenderOptions.concise()))

  def list: Action[AnyContent] =
    entrypoint("list configuration items")
      .authPermittedTransaction(db, Permissions.manageConfig) { implicit request => implicit graph =>
        if (!organisationSrv.current.isAdmin)
          Failure(AuthorizationError("You must be in `admin` organisation to view global configuration"))
        else
          Success(Results.Ok(Json.toJson(appConfig.list)))
      }

  def set(path: String): Action[AnyContent] =
    entrypoint("set configuration item")
      .extract("value", FieldsParser.json.on("value"))
      .authPermittedTransaction(db, Permissions.manageConfig) { implicit request => implicit graph =>
        if (!organisationSrv.current.isAdmin)
          Failure(AuthorizationError("You must be in `admin` organisation to change global configuration"))
        else {
          logger.info(s"app config value set: $path ${request.body("value")}")
          appConfig.set(path, request.body("value"))(request).map(_ => Results.NoContent)
        }
      }

  def get(path: String): Action[AnyContent] =
    entrypoint("get configuration item")
      .authPermittedTransaction(db, Permissions.manageConfig) { implicit request => implicit graph =>
        if (!organisationSrv.current.isAdmin)
          Failure(AuthorizationError("You must be in `admin` organisation to change global configuration"))
        else
          appConfig.get(path) match {
            case Some(c) => Success(Results.Ok(configWrites.writes(c)))
            case None    => Failure(NotFoundError(s"Configuration item $path not found"))
          }
      }

  def mergeConfig(defaultValue: JsValue, names: Seq[String], value: JsValue): JsValue =
    names
      .headOption
      .fold[JsValue](value) { key =>
        defaultValue
          .asOpt[JsObject]
          .fold(names.foldRight(value)((k, v) => Json.obj(k -> v))) { default =>
            default + (key -> mergeConfig((defaultValue \ key).getOrElse(JsNull), names.tail, value))
          }
      }

  def userList: Action[AnyContent] =
    entrypoint("list user configuration item")
      .extract("path", FieldsParser[String].optional.on("path"))
      .authRoTransaction(db) { implicit request => implicit graph =>
        val defaultValue = configuration.get[JsValue]("user.defaults")
        val userConfiguration = userSrv
          .current
          .config
          .toIterator
          .foldLeft(defaultValue)((default, config) => mergeConfig(default, config.name.split('.').toSeq, config.value))

        request.body("path") match {
          case Some(path: String) =>
            path
              .split('.')
              .foldLeft[JsLookupResult](JsDefined(userConfiguration))((cfg, key) => cfg \ key)
              .toOption
              .fold[Try[Result]](Failure(NotFoundError(s"The configuration $path doesn't exist")))(v => Success(Results.Ok(v)))
          case None =>
            Success(Results.Ok(userConfiguration))
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

  def organisationList: Action[AnyContent] =
    entrypoint("list organisation configuration item")
      .extract("path", FieldsParser[String].optional.on("path"))
      .authRoTransaction(db) { implicit request => implicit graph =>
        val defaultValue = configuration.get[JsValue]("organisation.defaults")
        val orgConfiguration = organisationSrv
          .current
          .config
          .toIterator
          .foldLeft(defaultValue)((default, config) => mergeConfig(default, config.name.split('.').toSeq, config.value))

        request.body("path") match {
          case Some(path: String) =>
            path
              .split('.')
              .foldLeft[JsLookupResult](JsDefined(orgConfiguration))((cfg, key) => cfg \ key)
              .toOption
              .fold[Try[Result]](Failure(NotFoundError(s"The configuration $path doesn't exist")))(v => Success(Results.Ok(v)))
          case None =>
            Success(Results.Ok(orgConfiguration))
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
