package org.thp.thehive.models

import org.thp.scalligraph.auth.{Permission, PermissionDesc, Permissions => Perms}

object Permissions extends Perms {
  lazy val manageCase: PermissionDesc               = PermissionDesc("manageCase", "Manage cases", "organisation")
  lazy val manageObservable: PermissionDesc         = PermissionDesc("manageObservable", "Manage observables", "organisation")
  lazy val manageAlert: PermissionDesc              = PermissionDesc("manageAlert", "Manage alerts", "organisation")
  lazy val manageUser: PermissionDesc               = PermissionDesc("manageUser", "Manage users", "organisation", "admin")
  lazy val manageOrganisation: PermissionDesc       = PermissionDesc("manageOrganisation", "Manage organisations", "admin")
  lazy val manageCaseTemplate: PermissionDesc       = PermissionDesc("manageCaseTemplate", "Manage case templates", "organisation")
  lazy val manageAnalyzerTemplate: PermissionDesc   = PermissionDesc("manageAnalyzerTemplate", "Manage analyzer templates", "admin")
  lazy val manageTask: PermissionDesc               = PermissionDesc("manageTask", "Manage tasks", "organisation")
  lazy val manageAction: PermissionDesc             = PermissionDesc("manageAction", "Run Cortex responders ", "organisation")
  lazy val manageConfig: PermissionDesc             = PermissionDesc("manageConfig", "Manage configurations", "organisation", "admin")
  lazy val manageProfile: PermissionDesc            = PermissionDesc("manageProfile", "Manage user profiles", "admin")
  lazy val manageTag: PermissionDesc                = PermissionDesc("manageTag", "Manage tags", "admin")
  lazy val manageCustomField: PermissionDesc        = PermissionDesc("manageCustomField", "Manage custom fields", "admin")
  lazy val manageShare: PermissionDesc              = PermissionDesc("manageShare", "Manage shares", "organisation")
  lazy val manageAnalyse: PermissionDesc            = PermissionDesc("manageAnalyse", "Run Cortex analyzer", "organisation")
  lazy val managePage: PermissionDesc               = PermissionDesc("managePage", "Manage pages", "organisation")
  lazy val manageObservableTemplate: PermissionDesc = PermissionDesc("manageObservableTemplate", "Manage observable types", "admin")
  lazy val managePattern: PermissionDesc            = PermissionDesc("managePattern", "Manage patterns", "admin")
  lazy val manageProcedure: PermissionDesc          = PermissionDesc("manageProcedure", "Manage procedures", "admin")
  lazy val accessTheHiveFS: PermissionDesc          = PermissionDesc("accessTheHiveFS", "Access to TheHiveFS", "organisation")

  lazy val list: Set[PermissionDesc] =
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
      manageShare,
      manageAnalyse,
      managePage,
      manageObservableTemplate,
      managePattern,
      manageProcedure,
      accessTheHiveFS
    )

  // These permissions are available only if the user is in admin organisation, they are removed for other organisations
  lazy val restrictedPermissions: Set[Permission]               = list.filter(_.scope == Seq("admin")).map(_.permission)
  def containsRestricted(permissions: Set[Permission]): Boolean = permissions.intersect(Permissions.restrictedPermissions).nonEmpty

  // This is the initial admin permissions
  lazy val adminPermissions: Set[Permission] = forScope("admin")
  override val defaultScopes: Seq[String]    = Seq("organisation")
}
