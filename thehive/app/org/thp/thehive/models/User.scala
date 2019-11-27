package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.auth.{Permission, User => ScalligraphUser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[User, Role]
case class UserRole()

@EdgeEntity[User, Attachment]
case class UserAttachment()

@DefineIndex(IndexType.unique, "login")
@VertexEntity
case class User(login: String, name: String, apikey: Option[String], locked: Boolean, password: Option[String]) extends ScalligraphUser {
  override val id: String          = login
  override def getUserName: String = name

  override def toString: String = s"User($login,$name,$locked)"
}

//    avatar: Array[Byte],
//    preference: JsObject)

case class RichUser(user: User with Entity, avatar: Option[String], profile: String, permissions: Set[Permission], organisation: String) {
  def _id: String                = user._id
  def _createdBy: String         = user._createdBy
  def _updatedBy: Option[String] = user._updatedBy
  def _createdAt: Date           = user._createdAt
  def _updatedAt: Option[Date]   = user._updatedAt
  def login: String              = user.login
  def name: String               = user.name
  def hasPassword: Boolean       = user.password.isDefined
  def apikey: Option[String]     = user.apikey
  def locked: Boolean            = user.locked
}
