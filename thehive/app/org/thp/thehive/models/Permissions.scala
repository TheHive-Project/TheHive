package org.thp.thehive.models

import org.thp.scalligraph.auth.Permission

object Permissions {
  val manageCase: Permission             = Permission("manageCase")
  val manageObservable: Permission       = Permission("manageObservable")
  val manageAlert: Permission            = Permission("manageAlert")
  val manageUser: Permission             = Permission("manageUser")
  val manageOrganisation: Permission     = Permission("manageOrganisation")
  val manageCaseTemplate: Permission     = Permission("manageCaseTemplate")
  val manageAnalyzerTemplate: Permission = Permission("manageAnalyzerTemplate")
  val manageTask: Permission             = Permission("manageTask")
  val manageAction: Permission           = Permission("manageAction")
  val manageConfig: Permission           = Permission("manageConfig")
  val manageProfile: Permission          = Permission("manageProfile")
  val manageTag: Permission              = Permission("manageTag")
  val manageCustomField: Permission      = Permission("manageCustomField")
  val manageShare: Permission            = Permission("manageShare")

  // These permissions are available only if the user is in default organisation, they are removed for other organisations
  val restrictedPermissions: Set[Permission] =
    Set(manageOrganisation, manageAnalyzerTemplate, manageConfig, manageProfile, manageCustomField, manageTag)

  // This is the initial admin permissions
  val adminPermissions: Set[Permission] = restrictedPermissions ++ Set(manageUser)

  val all: Set[Permission] =
    Set(
      manageCase,
      manageObservable,
      manageAlert,
      manageUser,
      manageOrganisation,
      manageCaseTemplate,
      manageAnalyzerTemplate,
      manageTask,
      manageAction,
      manageConfig,
      manageProfile,
      manageTag,
      manageCustomField,
      manageShare
    )
}
