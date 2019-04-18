package org.thp.thehive.models

import org.thp.scalligraph.auth.Permission

object Permissions {
  val manageCase: Permission         = Permission("manageCase")
  val manageAlert: Permission        = Permission("manageAlert")
  val manageUser: Permission         = Permission("manageUser")
  val manageOrganisation: Permission = Permission("manageOrganisation")
  val all: Set[Permission]           = Set(manageCase, manageAlert, manageUser, manageOrganisation)
}
