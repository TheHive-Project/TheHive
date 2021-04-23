package org.thp.thehive.shell

import org.thp.scalligraph.{EntityIdOrName, EntityName}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.thehive.models.Permissions

object MutableAuthContext extends AuthContext {
  override def userId: String    = _userId
  def setUserId(v: String): Unit = _userId = v
  private var _userId: String    = "admin@thehive.local"

  override def userName: String    = _userName
  def setUserName(v: String): Unit = _userName = v
  private var _userName            = "Admin"

  override def organisation: EntityIdOrName    = _organisation
  def setOrganisation(v: EntityIdOrName): Unit = _organisation = v
  private var _organisation: EntityIdOrName    = EntityName("admin")

  override def requestId: String    = _requestId
  def setRequestId(v: String): Unit = _requestId = v
  private var _requestId: String    = "shell"

  override def permissions: Set[Permission]    = _permissions
  def setPermissions(v: Set[Permission]): Unit = _permissions = v
  private var _permissions: Set[Permission]    = Permissions.all

  override def changeOrganisation(newOrganisation: EntityIdOrName, newPermissions: Set[Permission]): AuthContext = {
    _organisation = newOrganisation
    _permissions = newPermissions
    this
  }
}
