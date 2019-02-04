package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.models._
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

object UserStatus extends Enumeration {
  val ok, locked = Value
}

@EdgeEntity[User, Organisation]
case class UserOrganisation()

@DefineIndex(IndexType.unique, "login")
@VertexEntity
case class User(
    login: String,
    name: String,
    apikey: Option[String],
    @WithMapping(Permissions.mapping.sequence) permissions: Seq[Permission],
    status: UserStatus.Value,
    password: Option[String])
    extends org.thp.scalligraph.auth.User {
  override val id: String = login

  override def getUserName: String = name

  override def getPermissions: Seq[Permission] = permissions
}

//    avatar: Array[Byte],
//    preference: JsObject)

case class RichUser(user: User with Entity, organisation: String) {
  val _id: String                = user._id
  val _createdBy: String         = user._createdBy
  val _updatedBy: Option[String] = user._updatedBy
  val _createdAt: Date           = user._createdAt
  val _updatedAt: Option[Date]   = user._updatedAt
  val login: String              = user.login
  val name: String               = user.name
  val apikey: Option[String]     = user.apikey
  /*@WithMapping(Permissions.mapping.sequence) */
  val permissions: Seq[Permission] = user.permissions
  val status: UserStatus.Value     = user.status
  val password: Option[String]     = user.password
}
