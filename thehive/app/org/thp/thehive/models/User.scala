package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.models._
import org.thp.scalligraph.{EdgeEntity, VertexEntity}
import org.thp.scalligraph.auth.{User ⇒ ScalligraphUser}

object UserStatus extends Enumeration {
  val ok, locked = Value
}

@EdgeEntity[User, Role]
case class UserRole()

@DefineIndex(IndexType.unique, "login")
@VertexEntity
case class User(login: String, name: String, apikey: Option[String], status: UserStatus.Value, password: Option[String]) extends ScalligraphUser {
  override val id: String          = login
  override def getUserName: String = name
}

//    avatar: Array[Byte],
//    preference: JsObject)

case class RichUser(user: User with Entity, profile: String, permissions: Set[Permission], organisation: String) {
  val _id: String                = user._id
  val _createdBy: String         = user._createdBy
  val _updatedBy: Option[String] = user._updatedBy
  val _createdAt: Date           = user._createdAt
  val _updatedAt: Option[Date]   = user._updatedAt
  val login: String              = user.login
  val name: String               = user.name
  val apikey: Option[String]     = user.apikey
  val status: UserStatus.Value   = user.status
}
