package services.mappers

import javax.inject.Inject

import scala.concurrent.{ ExecutionContext, Future }

import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.WSClient

import org.elastic4play.AuthenticationError
import org.elastic4play.controllers.Fields

class GroupUserMapper(
    loginAttrName: String,
    nameAttrName: String,
    rolesAttrName: Option[String],
    groupAttrName: String,
    defaultRoles: Seq[String],
    groupsUrl: String,
    mappings: Map[String, Seq[String]],
    ws: WSClient,
    implicit val ec: ExecutionContext) extends UserMapper {

  @Inject() def this(

      configuration: Configuration,
      ws: WSClient,
      ec: ExecutionContext) = this(
    configuration.getOptional[String]("auth.sso.attributes.login").getOrElse("name"),
    configuration.getOptional[String]("auth.sso.attributes.name").getOrElse("username"),
    configuration.getOptional[String]("auth.sso.attributes.roles"),
    configuration.getOptional[String]("auth.sso.attributes.groups").getOrElse(""),
    configuration.getOptional[Seq[String]]("auth.sso.defaultRoles").getOrElse(Seq()),
    configuration.getOptional[String]("auth.sso.groups.url").getOrElse(""),
    configuration.getOptional[Map[String, Seq[String]]]("auth.sso.groups.mappings").getOrElse(Map()),
    ws,
    ec)

  override val name: String = "group"

  override def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)]): Future[Fields] = {

    val apiCall = authHeader.fold(ws.url(groupsUrl))(headers ⇒ ws.url(groupsUrl).addHttpHeaders(headers))
    apiCall.get.flatMap { r ⇒
      val jsonGroups = (r.json \ groupAttrName).as[Seq[String]]
      val mappedRoles = jsonGroups.flatMap(mappings.get).maxBy(_.length)
      val roles = if (mappedRoles.nonEmpty) mappedRoles else defaultRoles

      val fields = for {
        login ← (jsValue \ loginAttrName).validate[String]
        name ← (jsValue \ nameAttrName).validate[String]
      } yield Fields(Json.obj(
        "login" → login,
        "name" → name,
        "roles" → roles))
      fields match {
        case JsSuccess(f, _) ⇒ Future.successful(f)
        case JsError(errors) ⇒ Future.failed(AuthenticationError(s"User info fails: ${errors.map(_._1).mkString}"))
      }
    }
  }
}
