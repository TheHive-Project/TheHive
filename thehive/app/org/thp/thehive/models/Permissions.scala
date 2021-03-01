package org.thp.thehive.models

import org.thp.scalligraph.auth.{Permission, PermissionDesc, Permissions => Perms}

object Permissions extends Perms {
  lazy val accessTheHiveFS: PermissionDesc          = PermissionDesc("accessTheHiveFS", "Access to TheHiveFS", "organisation")
  lazy val manageAction: PermissionDesc             = PermissionDesc("manageAction", "Run Cortex responders ", "organisation")
  lazy val manageAlert: PermissionDesc              = PermissionDesc("manageAlert", "Manage alerts", "organisation")
  lazy val manageAnalyse: PermissionDesc            = PermissionDesc("manageAnalyse", "Run Cortex analyzer", "organisation")
  lazy val manageAnalyzerTemplate: PermissionDesc   = PermissionDesc("manageAnalyzerTemplate", "Manage analyzer templates", "admin")
  lazy val manageCase: PermissionDesc               = PermissionDesc("manageCase", "Manage cases", "organisation")
  lazy val manageCaseTemplate: PermissionDesc       = PermissionDesc("manageCaseTemplate", "Manage case templates", "organisation")
  lazy val manageConfig: PermissionDesc             = PermissionDesc("manageConfig", "Manage configurations", "organisation", "admin")
  lazy val manageCustomField: PermissionDesc        = PermissionDesc("manageCustomField", "Manage custom fields", "admin")
  lazy val manageObservable: PermissionDesc         = PermissionDesc("manageObservable", "Manage observables", "organisation")
  lazy val manageObservableTemplate: PermissionDesc = PermissionDesc("manageObservableTemplate", "Manage observable types", "admin")
  lazy val manageOrganisation: PermissionDesc       = PermissionDesc("manageOrganisation", "Manage organisations", "admin")
  lazy val managePage: PermissionDesc               = PermissionDesc("managePage", "Manage pages", "organisation")
  lazy val managePattern: PermissionDesc            = PermissionDesc("managePattern", "Manage patterns", "admin")
  lazy val manageProcedure: PermissionDesc          = PermissionDesc("manageProcedure", "Manage procedures", "organisation")
  lazy val manageProfile: PermissionDesc            = PermissionDesc("manageProfile", "Manage user profiles", "admin")
  lazy val manageShare: PermissionDesc              = PermissionDesc("manageShare", "Manage shares", "organisation")
  lazy val manageTag: PermissionDesc                = PermissionDesc("manageTag", "Manage tags", "admin")
  lazy val manageTaxonomy: PermissionDesc           = PermissionDesc("manageTaxonomy", "Manage taxonomies", "admin")
  lazy val manageTask: PermissionDesc               = PermissionDesc("manageTask", "Manage tasks", "organisation")
  lazy val manageUser: PermissionDesc               = PermissionDesc("manageUser", "Manage users", "organisation", "admin")
  lazy val managePlatform: PermissionDesc           = PermissionDesc("managePlatform", "Manage TheHive platform", "admin")

  lazy val list: Set[PermissionDesc] =
    Set(
      accessTheHiveFS,
      manageAction,
      manageAlert,
      manageAnalyse,
      manageAnalyzerTemplate,
      manageCase,
      manageCaseTemplate,
      manageConfig,
      manageCustomField,
      manageObservable,
      manageObservableTemplate,
      manageOrganisation,
      managePage,
      managePattern,
      manageProcedure,
      manageProfile,
      manageShare,
      manageTag,
      manageTask,
      manageTaxonomy,
      manageUser
    )

  // These permissions are available only if the user is in admin organisation, they are removed for other organisations
  lazy val restrictedPermissions: Set[Permission]               = list.filter(_.scope == Seq("admin")).map(_.permission)
  def containsRestricted(permissions: Set[Permission]): Boolean = permissions.intersect(Permissions.restrictedPermissions).nonEmpty

  // This is the initial admin permissions
  lazy val adminPermissions: Set[Permission] = forScope("admin")
  override val defaultScopes: Seq[String]    = Seq("organisation")
}
