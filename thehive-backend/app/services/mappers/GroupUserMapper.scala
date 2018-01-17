package services.mappers

import javax.inject.Inject

import org.elastic4play.AuthenticationError
import org.elastic4play.controllers.{ Fields, InputValue, StringInputValue }
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.{ ExecutionContext, Future }

class GroupUserMapper @Inject() (
    ws: WSClient,
    configuration: Configuration,
    implicit val ec: ExecutionContext) extends UserMapper {

  override val name: String = "group"
  override val defaultRoles: Seq[String] = configuration.getOptional[Seq[String]]("auth.sso.defaultRoles").getOrElse(Seq())
  private val groupsUrl: String = configuration.getOptional[String]("auth.sso.groups.url").getOrElse("")
  private val mappings: Map[String, Seq[String]] = configuration.getOptional[Map[String, Seq[String]]]("auth.sso.groups.mappings").getOrElse(Map())

  override def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)]): Future[Fields] = {

    val jsonUser = jsValue.validate[SimpleJsonUser].get
    val apiCall = if (authHeader.isDefined) ws.url(groupsUrl).addHttpHeaders(authHeader.get) else ws.url(groupsUrl)
    apiCall.get.flatMap { r ⇒
      val jsonGroups = r.json.validate[SimpleJsonGroups].get
      val roles = mappings.keys.filter(mapping ⇒ jsonGroups.groups.contains(mapping))
        .map(mappings(_)).toSeq.sortWith((left, right) ⇒ left.length > right.length)
      val role = if (roles.nonEmpty) {
        StringInputValue(roles.head)
      }
      else {
        if (defaultRoles.isEmpty) {
          throw AuthenticationError("No permissions setup for user")
        }
        StringInputValue(defaultRoles)
      }
      Future.successful {
        new Fields(Map[String, InputValue](
          "login" -> StringInputValue(jsonUser.username),
          "name" -> StringInputValue(jsonUser.name),
          "roles" -> role))
      }.recoverWith {
        case err ⇒
          Future.failed(err)
      }
    }.recoverWith {
      case err ⇒
        Future.failed(err)
    }
  }

}
