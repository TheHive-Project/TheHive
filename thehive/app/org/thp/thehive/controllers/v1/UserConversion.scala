package org.thp.thehive.controllers.v1

import scala.language.implicitConversions
import scala.util.Success

import play.api.libs.json.Json

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v1.{InputUser, OutputUser}
import org.thp.thehive.models.{Permissions, RichUser, User}
import org.thp.thehive.services.{UserSrv, UserSteps}

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
        .withFieldComputed(_.hasKey, _.apikey.isDefined)
        //        .withFieldComputed(_.permissions, _.permissions)
        //        .withFieldConst(_.permissions, Set.empty[String]
        .transform
    )

  def userProperties(userSrv: UserSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[UserSteps]
      .property("login", UniMapping.string)(_.simple.readonly)
      .property("name", UniMapping.string)(_.simple.custom { (_, value, vertex, db, graph, authContext) =>
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
          .flatMap { _ =>
            db.setProperty(vertex, "name", value, UniMapping.string)
            Success(Json.obj("name" -> value))
          }
      })
      .property("apikey", UniMapping.string)(_.simple.readonly)
      .property("locked", UniMapping.boolean)(_.simple.custom { (_, value, vertex, db, graph, authContext) =>
        userSrv
          .current(graph, authContext)
          .organisations(Permissions.manageUser)
          .users
          .get(vertex)
          .existsOrFail()
          .flatMap { _ =>
            db.setProperty(vertex, "locked", value, UniMapping.boolean)
            Success(Json.obj("locked" -> value))
          }
      })
      .build
}
