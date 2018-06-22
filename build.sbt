name := "TheHive"

lazy val thehiveBackend = (project in file("thehive-backend"))
  .enablePlugins(PlayScala)
  .settings(publish := {})

lazy val thehiveMetrics = (project in file("thehive-metrics"))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend)
  .settings(publish := {})

lazy val thehiveMisp = (project in file("thehive-misp"))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend)
  .settings(publish := {})

lazy val thehiveCortex = (project in file("thehive-cortex"))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend)
  .settings(publish := {})

lazy val thehive = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(PublishToBinTray)
  .dependsOn(thehiveBackend, thehiveMetrics, thehiveMisp, thehiveCortex)
  .aggregate(thehiveBackend, thehiveMetrics, thehiveMisp, thehiveCortex)
  .settings(aggregate in Debian := false)
  .settings(aggregate in Rpm := false)
  .settings(aggregate in Docker := false)

// Redirect logs from ElasticSearch (which uses log4j2) to slf4j
libraryDependencies += "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.9.1"
excludeDependencies += "org.apache.logging.log4j" % "log4j-core"

lazy val rpmPackageRelease = (project in file("package/rpm-release"))
  .enablePlugins(RpmPlugin)
  .settings(
    name := "thehive-project-release",
    maintainer := "TheHive Project <support@thehive-project.org>",
    version := "1.1.0",
    rpmRelease := "1",
    rpmVendor := "TheHive Project",
    rpmUrl := Some("http://thehive-project.org/"),
    rpmLicense := Some("AGPL"),
    maintainerScripts in Rpm := Map.empty,
    linuxPackageSymlinks in Rpm := Nil,
    packageSummary := "TheHive-Project RPM repository",
    packageDescription := """This package contains the TheHive-Project packages repository
      |GPG key as well as configuration for yum.""".stripMargin,
    linuxPackageMappings in Rpm := Seq(packageMapping(
      file("PGP-PUBLIC-KEY") -> "etc/pki/rpm-gpg/GPG-TheHive-Project",
      file("package/rpm-release/thehive-rpm.repo") -> "/etc/yum.repos.d/thehive-rpm.repo",
      file("LICENSE") -> "/usr/share/doc/thehive-project-release/LICENSE"
    ))
  )

// Front-end //
run := {
  (run in Compile).evaluated
  frontendDev.value
}
mappings in packageBin in Assets ++= frontendFiles.value

// Remove conf files
// Install service files
mappings in Universal ~= {
  _.flatMap {
    case (_, "conf/application.conf") => Nil
    case (file, "conf/application.sample") => Seq(file -> "conf/application.conf")
    case (_, "conf/logback.xml") => Nil
    case other => Seq(other)
  } ++ Seq(
    file("package/thehive.service") -> "package/thehive.service",
    file("package/thehive.conf") -> "package/thehive.conf",
    file("package/thehive") -> "package/thehive",
    file("package/logback.xml") -> "conf/logback.xml",
  ) ++ (file("migration").**(AllPassFilter) pair Path.rebase(file("migration"), "migration"))
}

// Package //
maintainer := "TheHive Project <support@thehive-project.org>"
packageSummary := "Scalable, Open Source and Free Security Incident Response Solutions"
packageDescription :=
  """TheHive is a scalable 3-in-1 open source and free security incident response
    | platform designed to make life easier for SOCs, CSIRTs, CERTs and any
    | information security practitioner dealing with security incidents that need to
    | be investigated and acted upon swiftly.""".stripMargin
defaultLinuxInstallLocation := "/opt"
linuxPackageMappings ~= {
  _.map { pm =>
    val mappings = pm.mappings.filterNot {
      case (_, path) => path.startsWith("/opt/thehive/package") || path.startsWith("/opt/thehive/conf")
    }
    com.typesafe.sbt.packager.linux.LinuxPackageMapping(mappings, pm.fileData)
  }
}
linuxPackageMappings ++= Seq(
  packageMapping(
    file("package/thehive.service") -> "/usr/lib/systemd/system/thehive.service"
  ).withPerms("644"),
  packageMapping(
    file("package/thehive.conf") -> "/etc/init/thehive.conf",
    file("conf/application.sample") -> "/etc/thehive/application.conf",
    file("package/logback.xml") -> "/etc/thehive/logback.xml"
  ).withPerms("644").withConfig(),
  packageMapping(
    file("package/thehive") -> "/etc/init.d/thehive"
  ).withPerms("755").withConfig())

packageBin := {
  (packageBin in Universal).value
  (packageBin in Debian).value
  (packageBin in Rpm).value
}

def getVersion(version: String): String = version.takeWhile(_ != '-')

def getRelease(version: String): String = {
  version.dropWhile(_ != '-').dropWhile(_ == '-') match {
    case "" => "1"
    case r if r.contains('-') => sys.error("Version can't have more than one dash")
    case r => s"0.1$r"
  }
}

// DEB //
linuxPackageMappings in Debian += packageMapping(file("LICENSE") -> "/usr/share/doc/thehive/copyright").withPerms("644")
version in Debian := getVersion(version.value) + '-' + getRelease(version.value)
debianPackageRecommends := Seq("elasticsearch")
debianPackageDependencies += "openjdk-8-jre-headless"
maintainerScripts in Debian := maintainerScriptsFromDirectory(
  baseDirectory.value / "package" / "debian",
  Seq(DebianConstants.Postinst, DebianConstants.Prerm, DebianConstants.Postrm)
)
linuxEtcDefaultTemplate in Debian := (baseDirectory.value / "package" / "etc_default_thehive").asURL
linuxMakeStartScript in Debian := None

// RPM //
version in Rpm := getVersion(version.value)
rpmRelease := getRelease(version.value)
rpmVendor := "TheHive Project"
rpmUrl := Some("http://thehive-project.org/")
rpmLicense := Some("AGPL")
rpmRequirements += "java-1.8.0-openjdk-headless"
maintainerScripts in Rpm := maintainerScriptsFromDirectory(
  baseDirectory.value / "package" / "rpm",
  Seq(RpmConstants.Pre, RpmConstants.Preun, RpmConstants.Postun)
)
linuxPackageSymlinks in Rpm := Nil
rpmPrefix := Some(defaultLinuxInstallLocation.value)
linuxEtcDefaultTemplate in Rpm := (baseDirectory.value / "package" / "etc_default_thehive").asURL
rpmReleaseFile := {
  import scala.sys.process._
  val rpmFile = (packageBin in Rpm in rpmPackageRelease).value
  s"rpm --addsign $rpmFile".!!
  rpmFile
}
packageBin in Rpm := {
  import scala.sys.process._
  val rpmFile = (packageBin in Rpm).value
  s"rpm --addsign $rpmFile".!!
  rpmFile
}

// DOCKER //
import com.typesafe.sbt.packager.docker.{ Cmd, ExecCmd }
version in Docker := getVersion(version.value) + '-' + getRelease(version.value)
defaultLinuxInstallLocation in Docker := "/opt/thehive"
dockerRepository := Some("certbdf")
dockerUpdateLatest := !version.value.contains('-')
dockerEntrypoint := Seq("/opt/thehive/entrypoint")
dockerExposedPorts := Seq(9000)
mappings in Docker ++= Seq(
  file("package/docker/entrypoint") -> "/opt/thehive/entrypoint",
  file("package/logback.xml") -> "/etc/thehive/logback.xml",
  file("package/empty") -> "/var/log/thehive/application.log")
mappings in Docker ~= (_.filterNot {
  case (_, filepath) => filepath == "/opt/thehive/conf/application.conf"
})
dockerCommands ~= { dc =>
  val (dockerInitCmds, dockerTailCmds) = dc
    .collect {
      case ExecCmd("RUN", "chown", _*) => ExecCmd("RUN", "chown", "-R", "daemon:root", ".")
      case other => other
    }
    .splitAt(4)
  dockerInitCmds ++
    Seq(
        Cmd("ADD", "var", "/var"),
      Cmd("ADD", "etc", "/etc"),
      ExecCmd("RUN", "chown", "-R", "daemon:root", "/var/log/thehive"),
      ExecCmd("RUN", "chmod", "+x", "/opt/thehive/bin/thehive", "/opt/thehive/entrypoint")) ++
    dockerTailCmds
}

// Bintray //
bintrayOrganization := Some("thehive-project")
publish := {
  (publish in Docker).value
  publishRelease.value
  publishLatest.value
  publishRpm.value
  publishDebian.value
}
