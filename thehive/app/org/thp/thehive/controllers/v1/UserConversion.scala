package org.thp.thehive.controllers.v1

import scala.language.implicitConversions
import scala.util.{Failure, Success}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.{Output, UnsupportedAttributeError}
import org.thp.thehive.dto.v1.{InputUser, OutputUser}
import org.thp.thehive.models.{Permissions, RichUser, User}
import org.thp.thehive.services.{UserSrv, UserSteps}
import play.api.libs.json.Json

import org.thp.scalligraph.models.UniMapping

object UserConversion {
  implicit def fromInputUser(inputUser: InputUser): User =
    inputUser
      .into[User]
      .withFieldComputed(_.id, _.login)
      .withFieldConst(_.apikey, None)
      .withFieldConst(_.password, None)
      .withFieldConst(_.locked, false)
      //      .withFieldComputed(_.permissions, _.permissions.flatMap(Permissions.withName)) // FIXME unkown permissions are ignored
      .transform

  implicit def toOutputUser(user: RichUser): Output[OutputUser] =
    Output[OutputUser](
      user
        .into[OutputUser]
        .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]])
        //        .withFieldComputed(_.permissions, _.permissions)
        //        .withFieldConst(_.permissions, Set.empty[String]
        .transform
    )

  def userProperties(userSrv: UserSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[UserSteps]
      .property("login", UniMapping.stringMapping)(_.simple.readonly)
      .property("name", UniMapping.stringMapping)(_.simple.custom { (path, value, vertex, db, graph, authContext) =>
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
            case _ if path.isEmpty =>
              db.setProperty(vertex, "name", value, UniMapping.stringMapping)
              Success(Json.obj("name" -> value))
            case _ => Failure(UnsupportedAttributeError(s"name.$path"))
          }
      })
      .property("apikey", UniMapping.stringMapping)(_.simple.readonly)
      .property("locked", UniMapping.booleanMapping)(_.simple.custom { (path, value, vertex, db, graph, authContext) =>
        userSrv
          .current(graph, authContext)
          .organisations(Permissions.manageUser)
          .users
          .get(vertex)
          .existsOrFail()
          .flatMap {
            case _ if path.isEmpty =>
              db.setProperty(vertex, "locked", value, UniMapping.booleanMapping)
              Success(Json.obj("locked" -> value))
            case _ => Failure(UnsupportedAttributeError(s"status.$path"))
          }
      })
      .build
}
