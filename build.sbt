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
  .settings(SbtScalariform.scalariformSettings: _*)

lazy val thehive = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend, thehiveMetrics, thehiveMisp, thehiveCortex)
  .aggregate(thehiveBackend, thehiveMetrics, thehiveMisp, thehiveCortex)
  .settings(aggregate in Debian := false)
  .settings(aggregate in Rpm := false)
  .settings(aggregate in Docker := false)
  .settings(PublishToBinTray.settings: _*)
  .settings(Release.settings: _*)


// Redirect logs from ElasticSearch (which uses log4j2) to slf4j
libraryDependencies += "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.9.0"
excludeDependencies += "org.apache.logging.log4j" % "log4j-core"

lazy val rpmPackageRelease = (project in file("package/rpm-release"))
  .enablePlugins(RpmPlugin)
  .settings(
    name := "thehive-project-release",
    maintainer := "TheHive Project <support@thehive-project.org>",
    version := "1.0.0",
    rpmRelease := "3",
    rpmVendor := "TheHive Project",
    rpmUrl := Some("http://thehive-project.org/"),
    rpmLicense := Some("AGPL"),
    maintainerScripts in Rpm := Map.empty,
    linuxPackageSymlinks in Rpm := Nil,
    packageSummary := "TheHive-Project RPM repository",
    packageDescription := """This package contains the TheHive-Project packages repository
      |GPG key as well as configuration for yum.""".stripMargin,
    logLevel in packageBin in Rpm := Level.Debug,
    linuxPackageMappings in Rpm := Seq(packageMapping(
      file("PGP-PUBLIC-KEY") -> "etc/pki/rpm-gpg/GPG-TheHive-Project",
      file("package/rpm-release/thehive-rpm.repo") -> "/etc/yum.repos.d/thehive-rpm.repo",
      file("LICENSE") -> "/usr/share/doc/thehive-project-release/LICENSE"
    ))
  )


Release.releaseVersionUIFile := baseDirectory.value / "ui" / "package.json"
Release.changelogFile := baseDirectory.value / "CHANGELOG.md"

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
    file("package/logback.xml") -> "conf/logback.xml"
  )
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
    file("conf/logback.xml") -> "/etc/thehive/logback.xml"
  ).withPerms("644").withConfig(),
  packageMapping(
    file("package/thehive") -> "/etc/init.d/thehive"
  ).withPerms("755").withConfig())

packageBin := {
  (packageBin in Universal).value
  (packageBin in Debian).value
  (packageBin in Rpm).value
}
// DEB //
linuxPackageMappings in Debian += packageMapping(file("LICENSE") -> "/usr/share/doc/thehive/copyright").withPerms("644")
version in Debian := version.value + "-1"
debianPackageRecommends := Seq("elasticsearch")
debianPackageDependencies += "openjdk-8-jre-headless"
maintainerScripts in Debian := maintainerScriptsFromDirectory(
  baseDirectory.value / "package" / "debian",
  Seq(DebianConstants.Postinst, DebianConstants.Prerm, DebianConstants.Postrm)
)
linuxEtcDefaultTemplate in Debian := (baseDirectory.value / "package" / "etc_default_thehive").asURL
linuxMakeStartScript in Debian := None

// RPM //
rpmRelease := "1"
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
  val rpmFile = (packageBin in Rpm in rpmPackageRelease).value
  s"rpm --addsign $rpmFile".!!
  rpmFile
}
packageBin in Rpm := {
  val rpmFile = (packageBin in Rpm).value
  s"rpm --addsign $rpmFile".!!
  rpmFile
}

// DOCKER //
import com.typesafe.sbt.packager.docker.{ Cmd, ExecCmd }
version in Docker := version.value + "-1"
defaultLinuxInstallLocation in Docker := "/opt/thehive"
dockerRepository := Some("certbdf")
dockerUpdateLatest := true
dockerEntrypoint := Seq("/opt/thehive/entrypoint")
dockerExposedPorts := Seq(9000)
mappings in Docker ++= Seq(
  file("package/docker/entrypoint") -> "/opt/thehive/entrypoint",
  file("conf/logback.xml") -> "/etc/thehive/logback.xml",
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
bintrayOrganization := Some("cert-bdf")
bintrayRepository := "thehive"
publish := {
  (publish in Docker).value
  PublishToBinTray.publishRelease.value
  PublishToBinTray.publishLatest.value
  PublishToBinTray.publishRpm.value
  PublishToBinTray.publishDebian.value
}

// Scalariform //
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

ScalariformKeys.preferences in ThisBuild := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, false)
  //  .setPreference(FirstParameterOnNewline, Force)
  .setPreference(AlignArguments, true)
  //  .setPreference(FirstArgumentOnNewline, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 60)
  .setPreference(CompactControlReadability, true)
  .setPreference(CompactStringConcatenation, false)
  .setPreference(DoubleIndentClassDeclaration, true)
  //  .setPreference(DoubleIndentMethodDeclaration, true)
  .setPreference(FormatXml, true)
  .setPreference(IndentLocalDefs, false)
  .setPreference(IndentPackageBlocks, false)
  .setPreference(IndentSpaces, 2)
  .setPreference(IndentWithTabs, false)
  .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
  //  .setPreference(NewlineAtEndOfFile, true)
  .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
  .setPreference(PreserveSpaceBeforeArguments, false)
  //  .setPreference(PreserveDanglingCloseParenthesis, false)
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(SpaceBeforeColon, false)
  //  .setPreference(SpaceBeforeContextColon, false)
  .setPreference(SpaceInsideBrackets, false)
  .setPreference(SpaceInsideParentheses, false)
  .setPreference(SpacesWithinPatternBinders, true)
  .setPreference(SpacesAroundMultiImports, true)
