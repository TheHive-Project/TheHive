package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.AuthorizationError
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.services.{ApplicationConfiguration, ConfigItem}
import org.thp.thehive.models.Permissions
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Failure, Success}

@Singleton
class ConfigCtrl @Inject()(appConfig: ApplicationConfiguration, entryPoint: EntryPoint) {

  implicit val configWrites: Writes[ConfigItem[_]] = Writes[ConfigItem[_]](
    item =>
      Json.obj(
        "path"         -> item.path,
        "description"  -> item.description,
        "defaultValue" -> item.getDefaultValueJson,
        "value"        -> item.getJson
      )
  )

  def list: Action[AnyContent] =
    entryPoint("list configuration items")
      .auth {
        case request if request.permissions.contains(Permissions.manageConfig) =>
          Success(Results.Ok(Json.toJson(appConfig.list)))
        case _ => Failure(AuthorizationError("You need manageConfig permission to view configuration"))
      }

  def set: Action[AnyContent] =
    entryPoint("set configuration item")
      .extract("path", FieldsParser.string)
      .extract("value", FieldsParser.json)
      .auth {
        case request if request.permissions.contains(Permissions.manageConfig) =>
          appConfig.set(request.body("path"), request.body("value")).map(_ => Results.NoContent)
        case _ => Failure(AuthorizationError("You need manageConfig permission to set configuration"))
      }
}
