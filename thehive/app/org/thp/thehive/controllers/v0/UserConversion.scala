package org.thp.thehive.controllers.v0

import scala.language.implicitConversions
import scala.util.{Failure, Success}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.{Output, UnsupportedAttributeError}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputUser, OutputUser}
import org.thp.thehive.models.{Permissions, RichUser, User, UserStatus}
import org.thp.thehive.services.{UserSrv, UserSteps}

trait UserConversion {
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
//        .withFieldComputed(_.roles, _.permissions.map(_.name).toSet) // FIXME
        .withFieldConst(_.roles, Set.empty[String]) // FIXME
        .withFieldConst(_.organisation, "") // FIXME
        .withFieldRenamed(_.login, _.id)
        .transform)
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
      .property[String]("status")(_.simple.custom[UserStatus.Value] { (prop, path, value, vertex, db, graph, authContext) ⇒
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
