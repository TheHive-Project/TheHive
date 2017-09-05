package models

import play.api.libs.json.{ JsString, JsValue }

import com.sksamuel.elastic4s.ElasticDsl.keywordField
import com.sksamuel.elastic4s.mappings.KeywordFieldDefinition
import org.scalactic.{ Every, Good, One, Or }
import models.JsonFormat.roleFormat

import org.elastic4play.{ AttributeError, InvalidFormatAttributeError }
import org.elastic4play.controllers.{ InputValue, JsonInputValue, StringInputValue }
import org.elastic4play.models.AttributeFormat
import org.elastic4play.services.Role

object Roles {
  object read extends Role("READ")
  object write extends Role("WRITE")
  object admin extends Role("ADMIN")
  object alert extends Role("ALERT")
  val roles: List[Role] = read :: write :: admin :: alert :: Nil

  val roleNames: List[String] = roles.map(_.name)
  def isValid(roleName: String): Boolean = roleNames.contains(roleName)
  def withName(roleName: String): Option[Role] = roles.find(_.name == roleName)
}

object RoleAttributeFormat extends AttributeFormat[Role]("role") {

  override def checkJson(subNames: Seq[String], value: JsValue): Or[JsValue, One[InvalidFormatAttributeError]] = value match {
    case JsString(v) if subNames.isEmpty && Roles.isValid(v) ⇒ Good(value)
    case _                                                   ⇒ formatError(JsonInputValue(value))
  }

  override def fromInputValue(subNames: Seq[String], value: InputValue): Role Or Every[AttributeError] = {
    if (subNames.nonEmpty)
      formatError(value)
    else
      (value match {
        case StringInputValue(Seq(v))    ⇒ Good(v)
        case JsonInputValue(JsString(v)) ⇒ Good(v)
        case _                           ⇒ formatError(value)
      }).flatMap(v ⇒ Roles.withName(v).fold[Role Or Every[AttributeError]](formatError(value))(role ⇒ Good(role)))

  }

  override def elasticType(attributeName: String): KeywordFieldDefinition = keywordField(attributeName)
}