import Common._

lazy val thehiveBackend = (project in file("thehive-backend"))
  .enablePlugins(PlayScala)
  .settings(projectSettings)
  .settings(publish := {})

lazy val thehiveMetrics = (project in file("thehive-metrics"))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend)
  .settings(projectSettings)
  .settings(publish := {})

lazy val thehiveMisp = (project in file("thehive-misp"))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend)
  .settings(projectSettings)
  .settings(publish := {})

lazy val thehiveCortex = (project in file("thehive-cortex"))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend)
  .settings(projectSettings)
  .settings(publish := {})

lazy val thehive = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(Bintray)
  .dependsOn(thehiveBackend, thehiveMetrics, thehiveMisp, thehiveCortex)
  .aggregate(thehiveBackend, thehiveMetrics, thehiveMisp, thehiveCortex)
  .settings(projectSettings)
  .settings(aggregate in Debian := false)
  .settings(aggregate in Rpm := false)
  .settings(aggregate in Docker := false)

lazy val rpmPackageRelease = (project in file("package/rpm-release"))
  .enablePlugins(RpmPlugin)
  .settings(projectSettings)
  .settings(
    name := "thehive-project-release",
    maintainer := "TheHive Project <support@thehive-project.org>",
    version := "1.1.0",
    rpmRelease := "2",
    rpmVendor := "TheHive Project",
    rpmUrl := Some("http://thehive-project.org/"),
    rpmLicense := Some("AGPL"),
    maintainerScripts in Rpm := Map.empty,
    linuxPackageSymlinks in Rpm := Nil,
    packageSummary := "TheHive-Project RPM repository",
    packageDescription :=
      """This package contains the TheHive-Project packages repository
        |GPG key as well as configuration for yum.""".stripMargin,
    linuxPackageMappings in Rpm := Seq(packageMapping(
      file("PGP-PUBLIC-KEY") -> "etc/pki/rpm-gpg/GPG-TheHive-Project",
      file("package/rpm-release/thehive-rpm.repo") -> "/etc/yum.repos.d/thehive-rpm.repo",
      file("LICENSE") -> "/usr/share/doc/thehive-project-release/LICENSE"
    ))
  )

rpmReleaseFile := {
  import scala.sys.process._
  val rpmFile = (packageBin in Rpm in rpmPackageRelease).value
  s"rpm --addsign $rpmFile".!!
  rpmFile
}

bintrayOrganization := Some("thehive-project")

// Front-end //
run := {
  (run in Compile).evaluated
  frontendDev.value
}
mappings in packageBin in Assets ++= frontendFiles.value

packageBin := {
  (packageBin in Universal).value
  (packageBin in Debian).value
  (packageBin in Rpm).value
}

publish := {
  (publish in Docker).value
  publishRelease.value
  publishLatest.value
  publishRpm.value
  publishDebian.value
}
