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

lazy val main = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend, thehiveMetrics, thehiveMisp, thehiveCortex)
  .aggregate(thehiveBackend, thehiveMetrics, thehiveMisp, thehiveCortex)
  .settings(aggregate in Docker := false)
  .settings(PublishToBinTray.settings: _*)
  .settings(Release.settings: _*)

releaseVersionUIFile := baseDirectory.value / "ui" / "package.json"

changelogFile := baseDirectory.value / "CHANGELOG.md"

// Front-end //
run := {
  (run in Compile).evaluated
  frontendDev.value
}

mappings in packageBin in Assets ++= frontendFiles.value

mappings in Universal ~= { _.filterNot {
  case (_, name) => name == "conf/application.conf"
}}

// Install files //

mappings in Universal ++= {
  val dir = baseDirectory.value / "install"
  (dir.***) pair relativeTo(dir.getParentFile)
}

// Release //
import ReleaseTransformations._

import Release._

bintrayOrganization := Some("cert-bdf")

bintrayRepository := "thehive"

publish := {
  (publish in Docker).value
  PublishToBinTray.publishRelease.value
  PublishToBinTray.publishLatest.value
}

releaseProcess := Seq[ReleaseStep](
  checkUncommittedChanges,
  checkSnapshotDependencies,
  getVersionFromBranch,
  runTest,
  releaseMerge,
  checkoutMaster,
  setReleaseVersion,
  setReleaseUIVersion,
  generateChangelog,
  commitChanges,
  tagRelease,
  publishArtifacts,
  checkoutDevelop,
  setNextVersion,
  setNextUIVersion,
  commitChanges,
  //commitNextVersion,
  pushChanges)

// DOCKER //

dockerBaseImage := "elasticsearch:2.3"

dockerExposedVolumes += "/data"

dockerRepository := Some("certbdf")

dockerUpdateLatest := true

mappings in Universal += file("docker/entrypoint") -> "bin/entrypoint"

import com.typesafe.sbt.packager.docker.{ ExecCmd, Cmd }

dockerCommands := dockerCommands.value.map {
  case ExecCmd("ENTRYPOINT", _*) => ExecCmd("ENTRYPOINT", "bin/entrypoint")
  case cmd                       => cmd
}

dockerCommands := (dockerCommands.value.head +:
  Cmd("EXPOSE", "9000") +:
  dockerCommands.value.tail)

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
