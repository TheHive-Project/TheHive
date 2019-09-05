package services.mappers

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

import play.api.Configuration
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}

import org.elastic4play.AuthenticationError
import org.elastic4play.controllers.Fields

class SimpleUserMapper(
    loginAttrName: String,
    nameAttrName: String,
    rolesAttrName: Option[String],
    defaultRoles: Seq[String],
    implicit val ec: ExecutionContext
) extends UserMapper {

  @Inject() def this(configuration: Configuration, ec: ExecutionContext) =
    this(
      configuration.getOptional[String]("auth.sso.attributes.login").getOrElse("sub"),
      configuration.getOptional[String]("auth.sso.attributes.name").getOrElse("name"),
      configuration.getOptional[String]("auth.sso.attributes.roles"),
      configuration.getOptional[Seq[String]]("auth.sso.defaultRoles").getOrElse(Seq()),
      ec
    )

  override val name: String = "simple"

  override def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)]): Future[Fields] = {
    val fields = for {
      login ← (jsValue \ loginAttrName).validate[String]
      name  ← (jsValue \ nameAttrName).validate[String]
      roles = rolesAttrName.fold(defaultRoles)(r ⇒ (jsValue \ r).asOpt[Seq[String]].getOrElse(defaultRoles))
    } yield Fields(Json.obj("login" → login, "name" → name, "roles" → roles))
    fields match {
      case JsSuccess(f, _) ⇒ Future.successful(f)
      case JsError(errors) ⇒ Future.failed(AuthenticationError(s"User info fails: ${errors.map(_._2).map(_.map(_.messages.mkString(", ")).mkString("; ")).mkString}"))
    }
  }
}
