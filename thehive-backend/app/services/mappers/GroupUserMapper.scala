package services.mappers

import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}
import scala.util.parsing.combinator._

import play.api.{Configuration, Logger}
import play.api.libs.json._
import play.api.libs.ws.WSClient

import org.elastic4play.AuthenticationError
import org.elastic4play.controllers.Fields

class GroupUserMapper(
    loginAttrName: String,
    nameAttrName: String,
    groupAttrName: String,
    defaultRoles: Seq[String],
    groupsUrl: String,
    mappings: Map[String, Seq[String]],
    ws: WSClient,
    implicit val ec: ExecutionContext
) extends UserMapper {

  @Inject() def this(configuration: Configuration, ws: WSClient, ec: ExecutionContext) =
    this(
      configuration.getOptional[String]("auth.sso.attributes.login").getOrElse("sub"),
      configuration.getOptional[String]("auth.sso.attributes.name").getOrElse("name"),
      configuration.getOptional[String]("auth.sso.attributes.groups").getOrElse(""),
      configuration.getOptional[Seq[String]]("auth.sso.defaultRoles").getOrElse(Seq()),
      configuration.getOptional[String]("auth.sso.groups.url").getOrElse(""),
      configuration.getOptional[Map[String, Seq[String]]]("auth.sso.groups.mappings").getOrElse(Map()),
      ws,
      ec
    )

  override val name: String = "group"

  private[GroupUserMapper] lazy val logger = Logger(getClass)

  private class RoleListParser extends RegexParsers {
    val str     = "[a-zA-Z0-9_]+".r
    val strSpc  = "[a-zA-Z0-9_ ]+".r
    val realStr = ("\""~>strSpc<~"\"" | "'"~>strSpc<~"'" | str)

    def expr: Parser[Seq[String]] = {
      "[" ~ opt(realStr ~ rep(("," | ",") ~ realStr)) ~ "]" ^^ {
        case _ ~ Some(firstRole ~ list) ~ _ => list.foldLeft(Seq(firstRole)) {
          case (queue, _ ~ role) => role +: queue
        }
        case _ ~ _      => Seq.empty[String]
      } | opt(realStr) ^^ {
        case Some(role) => Seq(role)
        case None       => Seq.empty[String]
      }
    }

    def apply(input: String, logger: Logger): Seq[String] = parseAll(expr, input) match {
      case Success(result, _)  => result
      case failure : NoSuccess => {
        logger.error(failure.msg)
        Seq.empty[String]
      }
    }
  }

  override def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)]): Future[Fields] = {
    if (groupsUrl == "") {
      logger.debug(s"No groups endpoint provided (auth.sso.groups.url). Assuming groups were already received.")

      val jsonGroups: Seq[String] = {
        val groups: JsValue = (jsValue \ groupAttrName).get
        // Groups received as valid JSON array
        if (groups.isInstanceOf[JsArray]) {
          groups.as[Seq[String]]
        // Group list received as string (invalid JSON, for example: "ROLE" or "['Role 1', ROLE2, 'Role_3']")
        } else if (groups.isInstanceOf[JsString]) {
          (new RoleListParser).apply(groups.as[String], logger)
        // Invalid group list
        } else {
          logger.error(s"Invalid group list (${groupAttrName} attribute, value '${groups}', type ${groups.getClass})")
          Seq.empty[String]
        }
      }

      mapGroupsAndBuildUserFields(jsValue, jsonGroups)

    } else {
      val apiCall = authHeader.fold(ws.url(groupsUrl))(headers ⇒ ws.url(groupsUrl).addHttpHeaders(headers))
      apiCall.get.flatMap { r ⇒
        val jsonGroups = (r.json \ groupAttrName).as[Seq[String]]
        mapGroupsAndBuildUserFields(jsValue, jsonGroups)
      }
    }
  }

  private def mapGroupsAndBuildUserFields(jsValue: JsValue, jsonGroups: Seq[String]): Future[Fields] = {
    val mappedRoles = jsonGroups.flatMap(mappings.get).flatten.toSet
    val roles       = if (mappedRoles.nonEmpty) mappedRoles else defaultRoles

    if (roles.isEmpty) {
      Future.failed(AuthenticationError(s"No matched roles for user."))

    } else {
      logger.debug(s"Computed roles : ${roles}")

      val fields = for {
        login ← (jsValue \ loginAttrName).validate[String]
        name  ← (jsValue \ nameAttrName).validate[String]
      } yield Fields(Json.obj("login" → login, "name" → name, "roles" → roles))
      fields match {
        case JsSuccess(f, _) ⇒ Future.successful(f)
        case JsError(errors) ⇒ Future.failed(AuthenticationError(s"User info fails: ${errors.map(_._1).mkString}"))
      }
    }
  }
}
