import Common._
import com.typesafe.sbt.packager.docker.{ Cmd, ExecCmd }

version in Docker := getVersion(version.value) + '-' + getRelease(version.value)
defaultLinuxInstallLocation in Docker := "/opt/thehive"
dockerRepository := Some("thehiveproject")
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