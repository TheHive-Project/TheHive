import Common.{betaVersion, snapshotVersion, stableVersion, versionUsage}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

version in Docker := {
  version.value match {
    case stableVersion(_, _)                      => version.value
    case betaVersion(v1, v2)                      => v1 + "-0.1RC" + v2
    case snapshotVersion(stableVersion(v1, v2))   => v1 + "-" + v2 + "-SNAPSHOT"
    case snapshotVersion(betaVersion(v1, v2, v3)) => v1 + "-0." + v3 + "RC" + v2 + "-SNAPSHOT"
    case _                                        => versionUsage(version.value)
  }
}
defaultLinuxInstallLocation in Docker := "/opt/thehive"
dockerRepository := Some("thehiveproject")
dockerUpdateLatest := !version.value.toUpperCase.contains("RC") && !version.value.contains("SNAPSHOT")
dockerEntrypoint := Seq("/opt/thehive/entrypoint")
dockerExposedPorts := Seq(9000)
daemonUser in Docker := "thehive"
daemonGroup in Docker := "thehive"
mappings in Docker ++= Seq(
  file("package/docker/entrypoint") -> "/opt/thehive/entrypoint",
  file("package/logback.xml")       -> "/etc/thehive/logback.xml",
  file("package/empty")             -> "/var/log/thehive/application.log"
)
mappings in Docker ~= (_.filterNot {
  case (_, filepath) => filepath == "/opt/thehive/conf/application.conf"
})
dockerCommands := Seq(
  Cmd("FROM", "openjdk:8"),
  Cmd("LABEL", "MAINTAINER=\"TheHive Project <support@thehive-project.org>\"", "repository=\"https://github.com/TheHive-Project/TheHive\""),
  Cmd("WORKDIR", "/opt/thehive"),
  // format: off
  Cmd("RUN",
    "apt", "update", "&&",
    "apt", "upgrade", "-y", "&&",
    "apt", "autoclean", "-y", "-q",  "&&",
    "apt", "autoremove", "-y", "-q",  "&&",
    "rm", "-rf", "/var/lib/apt/lists/*", "&&",
    "(", "type", "groupadd", "1>/dev/null", "2>&1", "&&",
      "groupadd", "-g", "1000", "thehive", "||",
      "addgroup", "-g", "1000", "-S", "thehive",
    ")", "&&",
    "(", "type", "useradd", "1>/dev/null", "2>&1", "&&",
      "useradd", "--system", "--uid", "1000", "--gid", "1000", "thehive", "||",
      "adduser", "-S", "-u", "1000", "-G", "thehive", "thehive",
    ")"),
  //format: on
  Cmd("ADD", "--chown=root:root", "opt", "/opt"),
  Cmd("ADD", "--chown=thehive:thehive", "var", "/var"),
  Cmd("ADD", "--chown=thehive:thehive", "etc", "/etc"),
  ExecCmd("RUN", "chmod", "+x", "/opt/thehive/bin/thehive", "/opt/thehive/entrypoint"),
  Cmd("RUN", "mkdir", "/data", "&&", "chown", "thehive:thehive", "/data"),
  Cmd("EXPOSE", "9000"),
  Cmd("USER", "thehive"),
  ExecCmd("ENTRYPOINT", "/opt/thehive/entrypoint"),
  ExecCmd("CMD")
)
