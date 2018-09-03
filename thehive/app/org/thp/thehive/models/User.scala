package org.thp.thehive.models

import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.models._
import org.thp.scalligraph.{PrivateField, VertexEntity}

object UserStatus extends Enumeration {
  val ok, locked = Value
}

@DefineIndex(IndexType.unique, "login")
@VertexEntity
case class User(
    login: String,
    name: String,
    @PrivateField apikey: Option[String],
    @PrivateField @WithMapping(Permissions.mapping.sequence) permissions: Seq[Permission],
    status: UserStatus.Value,
    @PrivateField password: Option[String])
    extends org.thp.scalligraph.auth.User {
  override val id: String = login

  override def getUserName: String = name

  override def getPermissions: Seq[Permission] = permissions
}

//    avatar: Array[Byte],
//    preference: JsObject)
