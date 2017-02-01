package models

import java.util.UUID

import scala.concurrent.Future
import scala.language.postfixOps

import play.api.libs.json.{ JsBoolean, JsObject, JsString, JsUndefined }
import play.api.libs.json.JsValue.jsValueToJsLookup

import org.elastic4play.models.{ AttributeDef, AttributeFormat ⇒ F, AttributeOption ⇒ O, BaseEntity, EntityDef, HiveEnumeration, ModelDef }
import org.elastic4play.services.JsonFormat.roleFormat
import org.elastic4play.services.Role

import JsonFormat.userStatusFormat
import services.AuditedModel

object UserStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Ok, Locked = Value
}

trait UserAttributes { _: AttributeDef ⇒
  val login = attribute("login", F.stringFmt, "Login of the user", O.form)
  val userId = attribute("_id", F.stringFmt, "User id (login)", O.model)
  val withKey = optionalAttribute("with-key", F.booleanFmt, "Generate an API key", O.form)
  val key = optionalAttribute("key", F.uuidFmt, "API key", O.model, O.sensitive, O.unaudited)
  val userName = attribute("name", F.stringFmt, "Full name (Firstname Lastname)")
  val roles = multiAttribute("roles", F.enumFmt(Role), "Comma separated role list (READ, WRITE and ADMIN)")
  val status = attribute("status", F.enumFmt(UserStatus), "Status of the user", UserStatus.Ok)
  val password = optionalAttribute("password", F.stringFmt, "Password", O.sensitive, O.unaudited)
  val avatar = optionalAttribute("avatar", F.stringFmt, "Base64 representation of user avatar image", O.unaudited)
  val preferences = attribute("preferences", F.stringFmt, "User preferences", "{}", O.sensitive, O.unaudited)
}

class UserModel extends ModelDef[UserModel, User]("user") with UserAttributes with AuditedModel {

  private def addKey = (attrs: JsObject) ⇒ (attrs \ "with-key") match {
    case _: JsUndefined ⇒ attrs
    case _              ⇒ attrs + ("key" → JsString(UUID.randomUUID.toString)) - "with-key"
  }

  private def setUserId = (attrs: JsObject) ⇒ (attrs \ "login").asOpt[JsString].fold(attrs) { login ⇒
    attrs - "login" + ("_id" → login)
  }

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject) = Future.successful(addKey.andThen(setUserId)(attrs))

  override def updateHook(user: BaseEntity, updateAttrs: JsObject): Future[JsObject] = Future.successful(addKey(updateAttrs))
}

class User(model: UserModel, attributes: JsObject) extends EntityDef[UserModel, User](model, attributes) with UserAttributes with org.elastic4play.services.User {
  override def toJson = super.toJson +
    ("has-key" → JsBoolean(key().isDefined))

  def toAdminJson = key().fold(toJson) { k ⇒ toJson + ("key" → JsString(k.toString)) }

  override def getUserName = userName()
  override def getRoles = roles()
}