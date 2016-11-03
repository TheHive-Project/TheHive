name := "TheHive"

lazy val thehiveBackend = (project in file("thehive-backend"))

lazy val thehiveMetrics = (project in file("thehive-metrics"))
  .dependsOn(thehiveBackend)
  
lazy val thehiveMisp = (project in file("thehive-misp"))
  .dependsOn(thehiveBackend)

lazy val main = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend, thehiveMetrics, thehiveMisp)
  .aggregate(thehiveBackend, thehiveMetrics, thehiveMisp)
  .settings(aggregate in Docker := false)

// Front-end //

val frontendDev = inputKey[Unit]("Build front-end in dev")

frontendDev := {
  val s = streams.value
  s.log.info("Preparing front-end for dev (grunt wiredep)")
  Process("grunt" :: "wiredep" :: Nil, baseDirectory.value / "ui") ! s.log
}

run := {
  (run in Compile).evaluated
  frontendDev.evaluated
}

val frontendFiles = taskKey[Seq[(File, String)]]("Front-end files")

frontendFiles := {
  val s = streams.value
  s.log.info("Preparing front-end for prod ...")
  s.log.info("npm install")
  Process("npm" :: "install" :: Nil, baseDirectory.value / "ui") ! s.log
  s.log.info("bower install")
  Process("bower" :: "install" :: Nil, baseDirectory.value / "ui") ! s.log
  s.log.info("grunt build")
  Process("grunt" :: "build" :: Nil, baseDirectory.value / "ui") ! s.log
  val dir = baseDirectory.value / "ui" / "dist"
  (dir.***) pair rebase(dir, "ui")
}

mappings in packageBin in Assets ++= frontendFiles.value

// Analyzers //

mappings in Universal ++= {
  val dir = baseDirectory.value / "analyzers"
  (dir.***) pair relativeTo(dir.getParentFile)
}

// BINTRAY //
publish := BinTray.publish(
	(packageBin in Universal).value,
	bintrayEnsureCredentials.value,
	bintrayOrganization.value,
	bintrayRepository.value,
	bintrayPackage.value,
	version.value,
	sLog.value)

bintrayOrganization := Some("cert-bdf")

bintrayRepository := "thehive"

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
