name := "TheHive"

lazy val thehiveBackend = (project in file("thehive-backend"))
  .settings(publish := {})

lazy val thehiveMetrics = (project in file("thehive-metrics"))
  .dependsOn(thehiveBackend)
  .settings(publish := {})

lazy val thehiveMisp = (project in file("thehive-misp"))
  .dependsOn(thehiveBackend)
  .settings(publish := {})

lazy val main = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend, thehiveMetrics, thehiveMisp)
  .aggregate(thehiveBackend, thehiveMetrics, thehiveMisp)
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

// Install files //

mappings in Universal ++= {
  val dir = baseDirectory.value / "install"
  (dir.***) pair relativeTo(dir.getParentFile)
}

// Analyzers //

mappings in Universal ++= {
  val dir = baseDirectory.value / "analyzers"
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
  ExecCmd("RUN", "bash", "-c",
    "apt-get update && " +
    "apt-get install -y --no-install-recommends python python-pip && " +
    "pip install OleFile && " +
    "rm -rf /var/lib/apt/lists/*") +:
  Cmd("EXPOSE", "9000") +:
  dockerCommands.value.tail)

// Scalariform //
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.defaultScalariformSettings

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
