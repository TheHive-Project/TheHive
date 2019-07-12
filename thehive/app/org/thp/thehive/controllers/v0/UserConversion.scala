package org.thp.thehive.controllers.v0

import scala.language.implicitConversions
import scala.util.{Failure, Success}

import play.api.libs.json.Json

import gremlin.scala.Key
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.controllers.FString
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.{InvalidFormatAttributeError, Output, UnsupportedAttributeError}
import org.thp.thehive.dto.v0.{InputUser, OutputUser}
import org.thp.thehive.models.{Permissions, RichUser, User}
import org.thp.thehive.services.{UserSrv, UserSteps}

object UserConversion {
  val adminPermissions: Set[Permission] = Set(Permissions.manageUser, Permissions.manageOrganisation)

  def permissions2Roles(permissions: Set[Permission]): Set[String] = {
    val roles =
      if ((permissions & adminPermissions).nonEmpty) Set("admin", "write", "read", "alert")
      else if ((permissions - Permissions.manageAlert).nonEmpty) Set("write", "read")
      else Set("read")
    if (permissions.contains(Permissions.manageAlert)) roles + "alert"
    else roles
  }
  implicit def fromInputUser(inputUser: InputUser): User =
    inputUser
      .into[User]
      .withFieldComputed(_.id, _.login)
      .withFieldConst(_.apikey, None)
      .withFieldConst(_.password, None)
      .withFieldConst(_.locked, false)
//    .withFieldRenamed(_.roles, _.permissions)
      .transform

  implicit def toOutputUser(user: RichUser): Output[OutputUser] = toOutputUser(user, true)

  def toOutputUser(user: RichUser, withKeyInfo: Boolean): Output[OutputUser] =
    Output[OutputUser](
      user
        .into[OutputUser]
        .withFieldComputed(_.roles, u => permissions2Roles(u.permissions))
        .withFieldRenamed(_.login, _.id)
        .withFieldComputed(_.hasKey, u => if (withKeyInfo) Some(u.apikey.isDefined) else None)
        .withFieldComputed(_.status, u => if (u.locked) "Locked" else "Ok")
        .transform
    )

  def userProperties(userSrv: UserSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[UserSteps]
      .property[String]("login")(_.simple.readonly)
      .property[String]("name")(_.simple.custom { (path, value, vertex, db, graph, authContext) =>
        def isCurrentUser =
          userSrv
            .current(graph, authContext)
            .get(vertex)
            .existsOrFail()

        def isUserAdmin =
          userSrv
            .current(graph, authContext)
            .organisations(Permissions.manageUser)
            .users
            .get(vertex)
            .existsOrFail()

        isCurrentUser
          .orElse(isUserAdmin)
          .map { _ =>
            db.setProperty(vertex, "name", value, UniMapping.stringMapping)
            Json.obj("name" -> value)
          }
      })
      .property[String]("apikey")(_.simple.readonly)
      .property[String]("status")(
        _.derived(_.choose(predicate = _.has(Key("locked") of true), onTrue = _.constant("Locked"), onFalse = _.constant("Ok")))
          .custom { (_, value, vertex, db, graph, authContext) =>
            userSrv
              .current(graph, authContext)
              .organisations(Permissions.manageUser)
              .users
              .get(vertex)
              .existsOrFail()
              .flatMap {
                case _ if value == "Ok" =>
                  db.setProperty(vertex, "locked", false, UniMapping.booleanMapping)
                  Success(Json.obj("status" -> value))
                case _ if value == "Locked" =>
                  db.setProperty(vertex, "locked", true, UniMapping.booleanMapping)
                  Success(Json.obj("status" -> value))
                case _ => Failure(InvalidFormatAttributeError("status", "UserStatus", Set("Ok", "Locked"), FString(value)))
              }
          }
      )
      .build
}
