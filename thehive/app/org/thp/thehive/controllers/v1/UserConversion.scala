package org.thp.thehive.controllers.v1

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.thehive.dto.v1.{InputUser, OutputUser}
import org.thp.thehive.models.{RichUser, User, UserStatus}

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
}
