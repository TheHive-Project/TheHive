package services.mappers

import javax.inject.Inject

import org.elastic4play.AuthenticationError
import org.elastic4play.controllers.{ Fields, InputValue, StringInputValue }
import play.api.Configuration
import play.api.libs.json.JsValue

import scala.concurrent.{ ExecutionContext, Future }

class SimpleUserMapper @Inject() (
    configuration: Configuration,
    implicit val ec: ExecutionContext)
  extends UserMapper {

  override val name: String = "simple"
  override val defaultRoles: Seq[String] = configuration.getOptional[Seq[String]]("auth.sso.defaultRoles").getOrElse(Seq())

  override def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)]): Future[Fields] = {
    if (defaultRoles.isEmpty) {
      throw AuthenticationError("No permissions setup for user")
    }
    val jsonUser = jsValue.validate[SimpleJsonUser].get
    Future.successful {
      new Fields(Map[String, InputValue](
        "login" -> StringInputValue(jsonUser.username),
        "name" -> StringInputValue(jsonUser.name),
        "roles" -> StringInputValue(defaultRoles)))
    }
  }

}
