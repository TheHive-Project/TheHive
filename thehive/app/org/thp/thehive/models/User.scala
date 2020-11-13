package org.thp.thehive.models

import java.util.Date

import org.thp.scalligraph.auth.{Permission, User => ScalligraphUser}
import org.thp.scalligraph.models._
import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity, EntityId}
import org.thp.thehive.services.LocalPasswordAuthSrv

@BuildEdgeEntity[User, Role]
case class UserRole()

@BuildEdgeEntity[User, Attachment]
case class UserAttachment()

@DefineIndex(IndexType.unique, "login")
@BuildVertexEntity
case class User(login: String, name: String, apikey: Option[String], locked: Boolean, password: Option[String], totpSecret: Option[String])
    extends ScalligraphUser {
  override val id: String          = login
  override def getUserName: String = name

  override def toString: String = s"User($login,$name,$locked)"
}

object User {
  val initPassword: String = "secret"

  val init: User = User(
    login = "admin@thehive.local",
    name = "Default admin user",
    apikey = None,
    locked = false,
    password = Some(LocalPasswordAuthSrv.hashPassword(initPassword)),
    totpSecret = None
  )

  val system: User =
    User(login = "system@thehive.local", name = "TheHive system user", apikey = None, locked = false, password = None, totpSecret = None)

  val initialValues: Seq[User] = Seq(init, system)
}

//    avatar: Array[Byte],
//    preference: JsObject)

case class RichUser(user: User with Entity, avatar: Option[String], profile: String, permissions: Set[Permission], organisation: String) {
  def _id: EntityId              = user._id
  def _createdBy: String         = user._createdBy
  def _updatedBy: Option[String] = user._updatedBy
  def _createdAt: Date           = user._createdAt
  def _updatedAt: Option[Date]   = user._updatedAt
  def login: String              = user.login
  def name: String               = user.name
  def hasPassword: Boolean       = user.password.isDefined
  def hasMFA: Boolean            = user.totpSecret.isDefined
  def apikey: Option[String]     = user.apikey
  def locked: Boolean            = user.locked
}
