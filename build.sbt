import Common._
import Dependencies._

lazy val thehiveBackend = (project in file("thehive-backend"))
  .enablePlugins(PlayScala)
  .settings(projectSettings)
  .settings(
    publish := {},
    libraryDependencies ++= Seq(
      Library.Play.cache,
      Library.Play.ws,
      Library.Play.ahc,
      Library.Play.filters,
      Library.Play.guice,
      Library.scalaGuice,
      Library.elastic4play,
      Library.zip4j,
      Library.reflections,
      Library.akkaCluster,
      Library.akkaClusterTools
    ),
    play.sbt.routes.RoutesKeys.routesImport -= "controllers.Assets.Asset"
  )

lazy val thehiveMisp = (project in file("thehive-misp"))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend)
  .settings(projectSettings)
  .settings(
    publish := {},
    libraryDependencies ++= Seq(
      Library.Play.ws,
      Library.Play.guice,
      Library.Play.ahc,
      Library.zip4j,
      Library.elastic4play
    )
  )

lazy val thehiveCortex = (project in file("thehive-cortex"))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend)
  .settings(projectSettings)
  .settings(
    publish := {},
    libraryDependencies ++= Seq(
      Library.Play.ws,
      Library.Play.guice,
      Library.Play.ahc,
      Library.elastic4play,
      Library.zip4j
    )
  )

lazy val thehive = (project in file("."))
  .enablePlugins(PlayScala /*, PlayAkkaHttp2Support*/ )
  .enablePlugins(Bintray)
  .dependsOn(thehiveBackend, thehiveMisp, thehiveCortex)
  .aggregate(thehiveBackend, thehiveMisp, thehiveCortex)
  .settings(projectSettings)
  .settings(
    aggregate in Debian := false,
    aggregate in Rpm := false,
    aggregate in Docker := false,
    aggregate in changeLog := false
  )
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
    linuxPackageMappings in Rpm := Seq(
      packageMapping(
        file("PGP-PUBLIC-KEY")                       → "etc/pki/rpm-gpg/GPG-TheHive-Project",
        file("package/rpm-release/thehive-rpm.repo") → "/etc/yum.repos.d/thehive-rpm.repo",
        file("LICENSE")                              → "/usr/share/doc/thehive-project-release/LICENSE"
      )
    )
  )

rpmReleaseFile := {
  import scala.sys.process._
  val rpmFile = (packageBin in Rpm in rpmPackageRelease).value
  Process(
    "rpm" ::
      "--define" :: "_gpg_name TheHive Project" ::
      "--define" :: "_signature gpg" ::
      "--define" :: "__gpg_check_password_cmd /bin/true" ::
      "--define" :: "__gpg_sign_cmd %{__gpg} gpg --batch --no-verbose --no-armor --use-agent --no-secmem-warning -u \"%{_gpg_name}\" -sbo %{__signature_filename} %{__plaintext_filename}" ::
      "--addsign" :: rpmFile.toString ::
      Nil
  ).!!
  rpmFile
}

import org.thp.ghcl.Milestone
milestoneFilter := ((milestone: Milestone) => milestone.title.matches("(?:[12].*|3\\.[01234].*)")) // < 3.5

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
