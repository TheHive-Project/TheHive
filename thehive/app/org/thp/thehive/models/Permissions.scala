package org.thp.thehive.models

import org.thp.scalligraph.auth.Permission

object Permissions {
  val manageCase: Permission           = Permission("manageCase")
  val manageObservable: Permission     = Permission("manageObservable")
  val manageAlert: Permission          = Permission("manageAlert")
  val manageUser: Permission           = Permission("manageUser")
  val manageOrganisation: Permission   = Permission("manageOrganisation")
  val manageCaseTemplate: Permission   = Permission("manageCaseTemplate")
  val manageReportTemplate: Permission = Permission("manageReportTemplate")
  val manageTask: Permission           = Permission("manageTask")
  val manageAction: Permission         = Permission("manageAction")
  val manageConfig: Permission         = Permission("manageConfig")

  val restrictedPermissions: Set[Permission] = Set(manageOrganisation, manageReportTemplate, manageConfig)

  val all: Set[Permission] =
    Set(
      manageCase,
      manageObservable,
      manageAlert,
      manageUser,
      manageOrganisation,
      manageCaseTemplate,
      manageTask,
      manageReportTemplate,
      manageAction,
      manageConfig
    )
}
