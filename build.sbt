name := "TheHive"

lazy val thehiveBackend = (project in file("thehive-backend"))
  .settings(publish := {})

lazy val thehiveMetrics = (project in file("thehive-metrics"))
  .dependsOn(thehiveBackend)
  .settings(publish := {})

lazy val thehiveMisp = (project in file("thehive-misp"))
  .dependsOn(thehiveBackend)
  .settings(publish := {})

lazy val thehiveCortex = (project in file("thehive-cortex"))
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

Release.releaseVersionUIFile := baseDirectory.value / "ui" / "package.json"
Release.changelogFile := baseDirectory.value / "CHANGELOG.md"

// Front-end //
run := {
  (run in Compile).evaluated
  frontendDev.value
}

mappings in packageBin in Assets ++= frontendFiles.value

// Remove conf files// Install service files //
mappings in Universal ~= {
  _.flatMap {
    case (file, "conf/application.conf") => Nil
    case (file, "conf/application.sample") => Seq(file -> "conf/application.conf")
    case other => Seq(other)
  } ++ Seq(
      file("install/thehive.service") -> "install/thehive.service",
      file("install/thehive.conf") -> "install/thehive.conf",
      file("install/thehive") -> "install/thehive"
    )
}

// Package //
maintainer := "Thomas Franco <toom@thehive-project.org"
packageSummary := "Scalable, Open Source and Free Security Incident Response Solutions"
packageDescription := """TheHive is a scalable 3-in-1 open source and free security incident response platform designed to make life easier
  | for SOCs, CSIRTs, CERTs and any information security practitioner dealing with security incidents that need to be
  | investigated and acted upon swiftly.""".stripMargin
defaultLinuxInstallLocation := "/opt"
linuxPackageMappings ~= { _.map { pm =>
    val mappings = pm.mappings.filterNot {
      case (file, path) => path.startsWith("/opt/thehive/install") || path.startsWith("/opt/thehive/conf")
    }
    com.typesafe.sbt.packager.linux.LinuxPackageMapping(mappings, pm.fileData).withConfig()
  } :+ packageMapping(
    file("install/thehive.service") -> "/etc/systemd/system/thehive.service",
    file("install/thehive.conf") -> "/etc/init/thehive.conf",
    file("install/thehive") -> "/etc/init.d/thehive",
    file("conf/application.sample") -> "/etc/thehive/application.conf",
    file("conf/logback.xml") -> "/etc/thehive/logback.xml"
  ).withConfig()
}

packageBin := {
  (packageBin in Universal).value
  (packageBin in Debian).value
  //(packageBin in Rpm).value
}
// DEB //
debianPackageRecommends := Seq("elasticsearch")
debianPackageDependencies += "java8-runtime-headless | java8-runtime"
maintainerScripts in Debian := maintainerScriptsFromDirectory(
  baseDirectory.value / "install" / "debian",
  Seq(DebianConstants.Postinst, DebianConstants.Prerm, DebianConstants.Postrm)
)
linuxEtcDefaultTemplate in Debian := (baseDirectory.value / "install" / "etc_default_thehive").asURL
linuxMakeStartScript in Debian := None

// RPM //
rpmRelease := "7"
rpmVendor in Rpm := "TheHive Project"
rpmUrl := Some("http://thehive-project.org/")
rpmLicense := Some("AGPL")
rpmRequirements += "java-1.8.0-openjdk-headless"
maintainerScripts in Rpm := maintainerScriptsFromDirectory(
  baseDirectory.value / "install" / "rpm",
  Seq(RpmConstants.Pre, RpmConstants.Preun, RpmConstants.Postun)
)
linuxPackageSymlinks in Rpm := Nil
rpmPrefix := Some(defaultLinuxInstallLocation.value)
linuxEtcDefaultTemplate in Rpm := (baseDirectory.value / "install" / "etc_default_thehive").asURL

// DOCKER //
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

dockerBaseImage := "elasticsearch:2.3"
dockerExposedVolumes += "/data"
dockerRepository := Some("certbdf")
dockerUpdateLatest := true
mappings in Docker += file("install/docker/entrypoint") -> "bin/entrypoint"

dockerCommands := dockerCommands.value.map {
  case ExecCmd("ENTRYPOINT", _*) => ExecCmd("ENTRYPOINT", "bin/entrypoint")
  case cmd => cmd
}

dockerCommands := (dockerCommands.value.head +:
  Cmd("EXPOSE", "9000") +:
  dockerCommands.value.tail)

// Bintray //
bintrayOrganization := Some("cert-bdf")
bintrayRepository := "thehive"
publish := {
  (publish in Docker).value
  PublishToBinTray.publishRelease.value
  PublishToBinTray.publishLatest.value
}

// Scalariform //
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
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
