package org.thp.thehive.controllers.v0

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.thehive.dto.v0.{InputUser, OutputUser}
import org.thp.thehive.models.{RichUser, User, UserStatus}

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
}
