package org.thp.thehive.models

import java.util.Date

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.{EdgeEntity, VertexEntity}
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.models._

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

case class RichUser(
    _id: String,
    _createdBy: String,
    _updatedBy: Option[String],
    _createdAt: Date,
    _updatedAt: Option[Date],
    login: String,
    name: String,
    apikey: Option[String],
    @WithMapping(Permissions.mapping.sequence) permissions: Seq[Permission],
    status: UserStatus.Value,
    password: Option[String],
    organisation: String)

object RichUser {
  def apply(user: User with Entity, organisation: String): RichUser =
    user
      .asInstanceOf[User]
      .into[RichUser]
      .withFieldConst(_._id, user._id)
      .withFieldConst(_._createdAt, user._createdAt)
      .withFieldConst(_._createdBy, user._createdBy)
      .withFieldConst(_._updatedAt, user._updatedAt)
      .withFieldConst(_._updatedBy, user._updatedBy)
      .withFieldConst(_.organisation, organisation)
      .transform
}
