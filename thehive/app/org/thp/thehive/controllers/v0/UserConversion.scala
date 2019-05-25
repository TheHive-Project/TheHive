package org.thp.thehive.controllers.v0

import scala.language.implicitConversions
import scala.util.{Failure, Success}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.{Output, UnsupportedAttributeError}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputUser, OutputUser}
import org.thp.thehive.models.{Permissions, RichUser, User, UserStatus}
import org.thp.thehive.services.{UserSrv, UserSteps}

trait UserConversion {
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
      .withFieldConst(_.status, UserStatus.ok)
//    .withFieldRenamed(_.roles, _.permissions)
      .transform

  implicit def toOutputUser(user: RichUser): Output[OutputUser] =
    Output[OutputUser](
      user
        .into[OutputUser]
        .withFieldComputed(_.roles, u ⇒ permissions2Roles(u.permissions))
        .withFieldRenamed(_.login, _.id)
        .transform
    )

  def userProperties(userSrv: UserSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[UserSteps]
      .property[String]("login")(_.simple.readonly)
      .property[String]("name")(_.simple.custom[String] { (prop, path, value, vertex, db, graph, authContext) ⇒
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
          .flatMap {
            case _ if path.isEmpty ⇒ Success(db.setProperty(vertex, "name", value, prop.mapping))
            case _                 ⇒ Failure(UnsupportedAttributeError(s"name.$path"))
          }
      })
      .property[String]("apikey")(_.simple.readonly)
      .property[String]("status")(_.derived(_.value[String]("status").map(_.capitalize)).custom[UserStatus.Value] {
        (prop, path, value, vertex, db, graph, authContext) ⇒
          userSrv
            .current(graph, authContext)
            .organisations(Permissions.manageUser)
            .users
            .get(vertex)
            .existsOrFail()
            .flatMap {
              case _ if path.isEmpty ⇒ Success(db.setProperty(vertex, "status", value.toString, prop.mapping))
              case _                 ⇒ Failure(UnsupportedAttributeError(s"status.$path"))
            }
      })
      .build
}
