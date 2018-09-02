package org.thp.thehive.models

import gremlin.scala.dsl.Converter
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.controllers.FieldsParser
import org.thp.scalligraph.models.{Mapping, SingleMapping}

object Permissions {

  object read extends Permission("read")

  object write extends Permission("write")

  object admin extends Permission("admin")

  object alert extends Permission("alert")

  val permissions: List[Permission] = read :: write :: admin :: alert :: Nil

  val permissionNames: List[String] = permissions.map(_.name)

  def isValid(permissionName: String): Boolean = permissionNames.contains(permissionName)

  def withName(permissionName: String): Option[Permission] = permissions.find(_.name == permissionName)

  def toPermission: PartialFunction[String, Permission] = Function.unlift(withName)

  val mapping: Mapping[Permission, Permission, String] =
    new SingleMapping[Permission, String](classOf[String], r ⇒ Some(r.name), withName(_).get)

  val parser: FieldsParser[Permission] = FieldsParser.string.map("")(toPermission)

  val converter: Converter[Permission] = new Converter[Permission] {
    override type GraphType = String
    override def toGraph(permission: Permission): String = permission.name
    override def toDomain(permissionName: String): Permission =
      withName(permissionName).getOrElse(sys.error(s"permission $permissionName is not valid"))
  }
}
