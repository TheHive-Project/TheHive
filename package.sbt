import Common.remapPath
// Remove conf files
// Install service files
mappings in Universal ~= {
  _.flatMap {
    case (_, "conf/application.conf")                => Nil
    case (file, "conf/application.sample.conf.conf") => Seq(file -> "conf/application.conf")
    case (_, "conf/logback.xml")                     => Nil
    case other                                       => Seq(other)
  } ++ Seq(
    file("package/logback.xml") -> "conf/logback.xml"
  )
}

// Package //
packageName := "thehive4"
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
    val mappings = pm
      .mappings
      .map(remapPath("thehive4", "thehive", "/etc", "/opt", "/var/log"))
      .filterNot {
        case (_, path) => path.startsWith("/opt/thehive/conf") || path.startsWith("/usr/bin") || path == "/etc/thehive/application.conf"
      }
    com.typesafe.sbt.packager.linux.LinuxPackageMapping(mappings, pm.fileData)
  }
}
linuxPackageMappings ++= Seq(
  packageMapping(
    file("package/thehive.service") -> "/usr/lib/systemd/system/thehive.service"
  ).withPerms("644"),
  packageMapping(
    file("conf/application.sample.conf") -> "/etc/thehive/application.conf",
    file("package/logback.xml")          -> "/etc/thehive/logback.xml"
  ).withPerms("644").withConfig()
)
daemonUser := "thehive"
bashScriptEnvConfigLocation := None
