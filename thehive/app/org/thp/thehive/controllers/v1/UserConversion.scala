package org.thp.thehive.controllers.v1

import scala.language.implicitConversions
import scala.util.{Failure, Success}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.{Output, UnsupportedAttributeError}
import org.thp.thehive.dto.v1.{InputUser, OutputUser}
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
