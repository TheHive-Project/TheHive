import Common.{stableVersion, betaVersion}
import com.typesafe.sbt.packager.docker.{ Cmd, ExecCmd }

version in Docker := {
  version.value match {
    case stableVersion(_, _) => version.value
    case betaVersion(v1, v2) => v1 + "-0.1RC" + v2
    case _ => sys.error("Invalid version: " + version.value)
  }
}
defaultLinuxInstallLocation in Docker := "/opt/thehive"
dockerRepository := Some("thehiveproject")
dockerUpdateLatest := !version.value.toUpperCase.contains("RC")
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