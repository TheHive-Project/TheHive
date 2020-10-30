package models

import scala.concurrent.Future

import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._

import models.JsonFormat.userStatusFormat
import services.AuditedModel

import org.elastic4play.models.{AttributeDef, BaseEntity, EntityDef, HiveEnumeration, ModelDef, AttributeFormat => F, AttributeOption => O}
import org.elastic4play.services.{User => EUser}

object UserStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Ok, Locked = Value
}

trait UserAttributes { _: AttributeDef =>
  val login       = attribute("login", F.userFmt, "Login of the user", O.form)
  val userId      = attribute("_id", F.stringFmt, "User id (login)", O.model)
  val key         = optionalAttribute("key", F.stringFmt, "API key", O.sensitive, O.unaudited)
  val userName    = attribute("name", F.stringFmt, "Full name (Firstname Lastname)")
  val roles       = multiAttribute("roles", RoleAttributeFormat, "Comma separated role list (READ, WRITE and ADMIN)")
  val status      = attribute("status", F.enumFmt(UserStatus), "Status of the user", UserStatus.Ok)
  val password    = optionalAttribute("password", F.stringFmt, "Password", O.sensitive, O.unaudited)
  val avatar      = optionalAttribute("avatar", F.stringFmt, "Base64 representation of user avatar image", O.unaudited)
  val preferences = attribute("preferences", F.stringFmt, "User preferences", "{}", O.sensitive, O.unaudited)
}

class UserModel extends ModelDef[UserModel, User]("user", "User", "/user") with UserAttributes with AuditedModel {

  override def removeAttribute: JsObject = Json.obj("status" -> UserStatus.Locked)

  private def setUserId(attrs: JsObject) = (attrs \ "login").asOpt[JsString].fold(attrs) { login =>
    attrs - "login" + ("_id" -> login)
  }

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = Future.successful(setUserId(attrs))
}

class User(model: UserModel, attributes: JsObject) extends EntityDef[UserModel, User](model, attributes) with UserAttributes with EUser {
  override def getUserName = userName()
  override def getRoles    = roles()

  override def toJson: JsObject =
    super.toJson +
      ("roles"  -> JsArray(roles().map(r => JsString(r.name.toLowerCase())))) +
      ("hasKey" -> JsBoolean(key().isDefined))
}
