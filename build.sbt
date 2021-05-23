import Common._
import Dependencies._
import com.typesafe.sbt.SbtNativePackager.autoImport.maintainer
import com.typesafe.sbt.packager.linux.LinuxPackageMapping
import com.typesafe.sbt.packager.linux.LinuxPlugin.autoImport.{daemonUser, linuxPackageMappings}
import org.thp.ghcl.Milestone
import sbt.file

import java.util.jar.Manifest

val defaultSettings = Seq(
  version := "4.1.4-1",
  organization := "org.thp",
  scalaVersion := "2.13.5",
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "Oracle Released Java Packages" at "https://download.oracle.com/maven",
    "TheHive project repository" at "https://dl.bintray.com/thehive-project/maven/"
  ),
  crossScalaVersions := Nil,
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-deprecation",         // Emit warning and location for usages of deprecated APIs.
    "-feature",             // Emit warning and location for usages of features that should be imported explicitly.
    "-unchecked",           // Enable additional warnings where generated code depends on assumptions.
    "-Xlint",               // Enable recommended additional warnings.
    "-Ywarn-numeric-widen", // Warn when numerics are widened.
    "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
    //"-Xfatal-warnings",   // Fail the compilation if there are any warnings.
    //"-Ywarn-adapted-args",// Warn if an argument list is modified to match the receiver.
    //"-Ywarn-dead-code",   // Warn when dead code is identified.
    //"-Ywarn-inaccessible",// Warn about inaccessible types in method signatures.
    //"-Ywarn-nullary-override",// Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
    //"-Ylog-classpath",
    //"-Xlog-implicits",
    //"-Yshow-trees-compact",
    //"-Yshow-trees-stringified",
    //"-Ymacro-debug-lite",
    "-Xlog-free-types",
    "-Xlog-free-terms",
    "-Xprint-types",
    "-Ymacro-annotations"
  ),
  scalafmtConfig := (ThisBuild / baseDirectory).value / ".scalafmt.conf",
  Test / fork := true,
  javaOptions ++= Seq(
    "-Xms512M",
    "-Xmx2048M",
    "-Xss1M",
    "-XX:+UseG1GC",
//    "-XX:+CMSClassUnloadingEnabled",
    "-XX:MaxPermSize=256M",
    "-XX:MaxMetaspaceSize=512m"
  ),
  dependencyOverrides += akkaActor,
  Compile / packageDoc / publishArtifact := false,
  Compile / doc / sources := Nil,
  Test / packageDoc / publishArtifact := false,
  Test / doc / sources := Nil
)

val defaultPackageSettings = Seq(
  maintainer := "TheHive Project <support@thehive-project.org>",
  packageSummary := "Scalable, Open Source and Free Security Incident Response Solutions",
  packageDescription :=
    """TheHive is a scalable 3-in-1 open source and free security incident response
      | platform designed to make life easier for SOCs, CSIRTs, CERTs and any
      | information security practitioner dealing with security incidents that need to
      | be investigated and acted upon swiftly.""".stripMargin,
  defaultLinuxInstallLocation := "/opt",
  daemonUser := "thehive",
  Debian / version := debianVersion(version.value),
  Debian / linuxPackageSymlinks := Nil,
  Rpm / version := rpmVersion(version.value),
  rpmRelease := rpmReleaseVersion(version.value),
  rpmVendor := organizationName.value,
  rpmUrl := organizationHomepage.value.map(_.toString),
  rpmLicense := Some("AGPL"),
  rpmPrefix := Some(defaultLinuxInstallLocation.value),
  Rpm / linuxPackageSymlinks := Nil,
  Rpm / packageBin ~= rpmSignFile,
  stage := {
    (Universal / stage).value
    (Debian / stage).value
    (Rpm / stage).value
  },
  packageBin := {
    (Universal / packageBin).value
    (Debian / packageBin).value
    (Rpm / packageBin).value
  }
)

val noPackageSettings = Seq(
  Universal / stage := file(""),
  Universal / packageBin := file(""),
  Debian / stage := file(""),
  Debian / packageBin := file(""),
  Rpm / stage := file(""),
  Rpm / packageBin := file(""),
  publish / skip := true
)

milestoneFilter := ((milestone: Milestone) => milestone.title.startsWith("4"))

lazy val scalligraphRoot = ProjectRef(file("scalligraph"), "scalligraphRoot")

lazy val scalligraph = ProjectRef(file("scalligraph"), "scalligraph")

lazy val scalligraphTest = ProjectRef(file("scalligraph"), "scalligraphTest")

lazy val scalligraphJanusgraph = ProjectRef(file("scalligraph"), "scalligraphJanusgraph")

lazy val thehiveRoot = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend, thehiveFrontend, thehiveMigration)
  .aggregate(
    thehive,
    thehiveBackend,
    thehiveCore,
    thehiveDto,
    thehiveClient,
    thehiveShell,
    thehiveFrontend,
    thehiveFrontendJar,
    clientCommon,
    thehiveCortex,
    thehiveMisp,
    mispClient,
    thehiveMigration
  )
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "root",
    packageName := "root",
    PlayKeys.playMonitoredFiles ~= (_.filter(f => f.compareTo(file("frontend").getAbsoluteFile) != 0)),
    run / fork := true,
    run := {
      (thehiveFrontend / gruntDev).value
      (Compile / runMain).toTask(" org.thp.thehive.TheHiveStarter . --dev").value
    }
  )

lazy val thehive = (project in file("target/thehive"))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveBackend, thehiveFrontend, thehiveMigration)
  .aggregate(
    thehiveFrontendJar,
    thehiveMigration,
    thehiveBackend,
    thehiveShell,
    scalligraphRoot
  )
  .settings(defaultSettings)
  .settings(defaultPackageSettings)
  .settings(
    name := "thehive",
    packageName := "thehive4",
    PlayKeys.playMonitoredFiles ~= (_.filter(f => f.compareTo(file("frontend/app").getAbsoluteFile) != 0)),
    PlayKeys.devSettings += "play.server.provider" -> "org.thp.thehive.CustomAkkaHttpServerProvider",
    Universal / mappings := Nil,
    Debian / linuxPackageMappings := Nil,
    Debian / debianPackageDependencies := Seq(
      s"thehive4-backend (= ${version.value})",
      s"thehive4-frontend-jar (= ${version.value})",
      s"thehive4-migration (= ${version.value})"
    ),
    Rpm / linuxPackageMappings := Nil,
    rpmRequirements := Seq(
      s"thehive4-backend = ${version.value}",
      s"thehive4-frontend-jar = ${version.value}",
      s"thehive4-migration = ${version.value}"
    )
  )

lazy val thehiveBackend = (project in file("target/thehive-backend"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(thehiveCore, thehiveMisp, thehiveCortex)
  .aggregate(thehiveCore, thehiveMisp, thehiveCortex)
  .settings(defaultSettings)
  .settings(defaultPackageSettings)
  .settings(
    name := "thehive-backend",
    packageName := "thehive4-backend",
    Compile / mainClass := Some("org.thp.thehive.TheHiveStarter"),
    Universal / mappings ++= Seq(
      (ThisBuild / baseDirectory).value / "conf" / "application.sample.conf" -> "conf/application.conf",
      (ThisBuild / baseDirectory).value / "package" / "logback.xml"          -> "conf/logback.xml",
      (ThisBuild / baseDirectory).value / "package" / "thehive.service"      -> "conf/thehive.service",
      (ThisBuild / baseDirectory).value / "LICENSE"                          -> "LICENSE"
    ),
    linuxPackageMappings := (
      linuxPackageMappings.value.map { packageMapping =>
        val newMapping = packageMapping
          .mappings
          .filterNot(_._2.startsWith("/opt/thehive4-backend/conf"))
          .map(remapPath("thehive4-backend", "thehive", "/opt"))
        LinuxPackageMapping(newMapping, packageMapping.fileData)
      } :+ LinuxPackageMapping(
        Seq(
          (ThisBuild / baseDirectory).value / "package" / "thehive.default"      -> "/etc/default/thehive",
          (ThisBuild / baseDirectory).value / "conf" / "application.sample.conf" -> "/etc/thehive/application.conf",
          (ThisBuild / baseDirectory).value / "package" / "logback.xml"          -> "/etc/thehive/logback.xml"
        )
      ).withPerms("644").withConfig() :+ LinuxPackageMapping(
        Seq(
          (ThisBuild / baseDirectory).value / "package" / "thehive.service" -> "/usr/lib/systemd/system/thehive.service",
          (ThisBuild / baseDirectory).value / "LICENSE"                     -> "/usr/share/doc/thehive/copyright"
        )
      ).withPerms("644")
    ),
    bashScriptDefines / scriptClasspath ++= Seq(
      s"org.thp.thehive-frontend-${version.value}.jar",
      s"org.thp.thehive-frontend-assets-${version.value}.jar",
      s"org.thp.thehive-enterprise-module-${version.value}.jar",
      s"org.thp.thehive-enterprise-frontend-${version.value}.jar",
      s"org.thp.thehive-enterprise-frontend-assets-${version.value}.jar"
    ),
    normalizedName := "thehive",
    Debian / linuxPackageMappings := linuxPackageMappings.value,
    Debian / debianPackageDependencies += "java8-runtime-headless",
    Debian / maintainerScripts := maintainerScriptsFromDirectory(
      (ThisBuild / baseDirectory).value / "package" / "debian",
      Seq(DebianConstants.Postinst, DebianConstants.Prerm, DebianConstants.Postrm)
    ),
    Debian / linuxMakeStartScript := None,
    rpmRequirements += "java-1.8.0-openjdk-headless",
    Rpm / maintainerScripts := maintainerScriptsFromDirectory(
      (ThisBuild / baseDirectory).value / "package" / "rpm",
      Seq(RpmConstants.Pre, RpmConstants.Post, RpmConstants.Preun, RpmConstants.Postun)
    ),
    Rpm / linuxPackageMappings := configWithNoReplace((Rpm / linuxPackageMappings).value)
  )

lazy val thehiveCore = (project in file("thehive"))
  .enablePlugins(PlayScala)
  .dependsOn(scalligraph, scalligraphJanusgraph, scalligraphTest % "test -> test")
  .dependsOn(thehiveDto, clientCommon, /* cortexClient % "test -> test",*/ thehiveClient % Test)
  .aggregate(thehiveDto, thehiveClient)
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "thehive-core",
    libraryDependencies ++= Seq(
      chimney,
      akkaCluster,
      akkaClusterTyped,
      akkaClusterTools,
      zip4j,
      ws,
      specs % Test,
      handlebars,
      playMailer,
      pbkdf2,
      commonCodec,
      reflections,
      macWireMacros,
      macWireMacrosakka,
      macWireUtil,
      macWireProxy
    )
  )

lazy val thehiveDto = (project in file("dto"))
  .dependsOn(scalligraph) // required for parsers
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "thehive-dto",
    libraryDependencies ++= Seq(
      aix,
      refined,
      playRefined
    )
  )

lazy val thehiveClient = (project in file("client"))
  .dependsOn(thehiveDto, clientCommon)
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "thehive-client",
    libraryDependencies ++= Seq(
      ws
    )
  )

lazy val thehiveShell = (project in file("shell"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(thehiveCore)
  .settings(defaultSettings)
  .settings(defaultPackageSettings)
  .settings(
    name := "thehive-shell",
    packageName := "thehive4-shell",
    javaOptions := Seq("-XX:+UseG1GC", "-Xmx4G"),
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    run / connectInput := true,
    run / fork := true,
    libraryDependencies ++= Seq(
      ammonite,
      macWireMacros,
      macWireMacrosakka,
      macWireUtil,
      macWireProxy
    ),
    normalizedName := "thehive-shell",
    Compile / mainClass := Some("org.thp.thehive.shell.Shell"),
    Universal / mappings := {
      val backendMappings = (thehiveBackend / Universal / mappings).value.map(_._2).toSet
      (Universal / mappings).value.filterNot(m => backendMappings.contains(m._2))
    },
    linuxPackageMappings ~= {
      _.map { packageMappings =>
        val newMapping = packageMappings
          .mappings
          .map(remapPath("thehive4-shell", "thehive", "/opt"))
        LinuxPackageMapping(newMapping, packageMappings.fileData)
      }
    },
    Debian / linuxPackageMappings := linuxPackageMappings.value,
    Debian / debianPackageDependencies ++= Seq("java8-runtime-headless", s"thehive4-backend (= ${version.value})"),
    rpmRequirements ++= Seq("java-1.8.0-openjdk-headless", s"thehive4-backend = ${version.value}"),
    Rpm / linuxPackageMappings := configWithNoReplace((Rpm / linuxPackageMappings).value)
  )

lazy val npm        = taskKey[Unit]("Install npm dependencies")
lazy val bower      = taskKey[Unit]("Install bower dependencies")
lazy val gruntDev   = taskKey[Unit]("Inject bower dependencies in index.html")
lazy val gruntBuild = taskKey[Seq[(File, String)]]("Build frontend files")

lazy val thehiveFrontend = (project in file("frontend"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(scalligraph)
  .settings(defaultSettings)
  .settings(defaultPackageSettings)
  .settings(
    name := "thehive-frontend",
    packageName := "thehive4-frontend",
    npm :=
      FileBuilder(
        label = "thehive-frontend:npm",
        inputFiles = baseDirectory.value / "package.json",
        outputFiles = baseDirectory.value / "node_modules" ** AllPassFilter,
        command = baseDirectory.value -> "npm install",
        streams = streams.value
      ),
    bower := FileBuilder(
      label = "thehive-frontend:bower",
      inputFiles = baseDirectory.value / "bower.json",
      outputFiles = baseDirectory.value / "bower_components" ** AllPassFilter,
      command = baseDirectory.value -> "bower install",
      streams = streams.value
    ),
    gruntDev := {
      npm.value
      bower.value
      FileBuilder(
        label = "thehive-frontend:grunt",
        inputFiles = baseDirectory.value / "bower_components" ** AllPassFilter,
        outputFiles = baseDirectory.value / "app" / "index.html",
        command = baseDirectory.value -> "grunt wiredep",
        streams = streams.value
      )
    },
    gruntBuild := {
      npm.value
      bower.value
      val dist = baseDirectory.value / "dist"
      FileBuilder(
        label = "thehive-frontend:grunt",
        inputFiles = baseDirectory.value / "bower_components" ** AllPassFilter,
        outputFiles = dist ** AllPassFilter,
        command = baseDirectory.value -> "grunt build",
        streams = streams.value
      )
      sbt.Path.contentOf(dist).filterNot(_._1.isDirectory)
    },
    cleanFiles ++= Seq(
      baseDirectory.value / "dist",
      baseDirectory.value / "bower_components",
      baseDirectory.value / "node_modules"
    ),
    Runtime / dependencyClasspath := Nil,
    Universal / mappings := gruntBuild.value.map {
      case (file, path) => file -> s"/opt/thehive/frontend/$path"
    },
    linuxPackageMappings := Seq(LinuxPackageMapping((Universal / mappings).value.filterNot(_._2.endsWith("jar"))).withPerms("644")),
    Debian / linuxPackageMappings := linuxPackageMappings.value,
    Debian / debianPackageRecommends += "thehive4-frontend"
  )

lazy val thehiveFrontendJar = (project in file("target/thehive-frontend-jar"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(thehiveFrontend)
  .aggregate(thehiveFrontend)
  .settings(defaultSettings)
  .settings(defaultPackageSettings)
  .settings(
    name := "thehive-frontend-jar",
    packageName := "thehive4-frontend-jar",
    Universal / mappings := {
      val jarFile = target.value / "thehive-frontend.jar"
      Package.makeJar(
        sources = (thehiveFrontend / Universal / mappings).value.map(rebasePath("/opt/thehive", "community")),
        jar = jarFile,
        manifest = new Manifest,
        log = streams.value.log,
        time = None
      )
      Seq(jarFile -> s"lib/org.thp.thehive-frontend-assets-${version.value}.jar")
    },
    linuxPackageMappings := {
      val moduleJar = (thehiveFrontend / Compile / packageBin).value

      linuxPackageMappings.value.map { packageMapping =>
        val newMapping = packageMapping
          .mappings
          .map(remapPath("thehive4-frontend-jar", "thehive", "/opt"))
        LinuxPackageMapping(newMapping, packageMapping.fileData)
      } :+ LinuxPackageMapping(Seq(moduleJar -> s"/opt/thehive/lib/org.thp.thehive-frontend-${version.value}.jar"))
    },
    Debian / linuxPackageMappings := linuxPackageMappings.value,
    Rpm / linuxPackageMappings := linuxPackageMappings.value
  )

lazy val clientCommon = (project in file("client-common"))
  .dependsOn(scalligraph)
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "client-common",
    libraryDependencies ++= Seq(
      ws,
      specs % Test
    )
  )

lazy val thehiveCortex = (project in file("cortex/connector"))
  .dependsOn(thehiveCore)
  .dependsOn(cortexClient)
  .dependsOn(cortexClient % "test -> test")
  .dependsOn(thehiveCore % "test -> test")
  .dependsOn(scalligraphTest % "test -> test")
  .aggregate(cortexClient)
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "thehive-cortex",
    libraryDependencies ++= Seq(
      reflections,
      specs % Test,
      macWireMacros,
      macWireMacrosakka,
      macWireUtil,
      macWireProxy
    )
  )

lazy val cortexDto = (project in file("cortex/dto"))
  .dependsOn(scalligraph)
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "cortex-dto",
    libraryDependencies ++= Seq(
      chimney
    )
  )

lazy val cortexClient = (project in file("cortex/client"))
  .dependsOn(cortexDto)
  .dependsOn(clientCommon)
  .dependsOn(scalligraphTest % "test -> test")
  .aggregate(cortexDto, clientCommon)
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "cortex-client",
    libraryDependencies ++= Seq(
      ws,
      specs            % Test,
      playFilters      % Test,
      playMockws       % Test,
      akkaClusterTyped % Test
    )
  )

lazy val thehiveMisp = (project in file("misp/connector"))
  .dependsOn(thehiveCore)
  .dependsOn(mispClient)
  .dependsOn(thehiveCore % "test -> test")
  .aggregate(mispClient)
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "thehive-misp",
    libraryDependencies ++= Seq(
      specs      % Test,
      playMockws % Test,
      macWireMacros,
      macWireMacrosakka,
      macWireUtil,
      macWireProxy
    )
  )

lazy val mispClient = (project in file("misp/client"))
  .dependsOn(scalligraph)
  .dependsOn(clientCommon)
  .settings(defaultSettings)
  .settings(noPackageSettings)
  .settings(
    name := "misp-client",
    libraryDependencies ++= Seq(
      ws,
      alpakka,
      akkaHttp,
      specs      % Test,
      playMockws % Test
    )
  )

lazy val thehiveMigration = (project in file("migration"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(scalligraph)
  .dependsOn(thehiveCore)
  .dependsOn(thehiveCortex)
  .settings(defaultSettings)
  .settings(defaultPackageSettings)
  .settings(
    name := "thehive-migration",
    packageName := "thehive4-migration",
    resolvers += "elasticsearch-releases" at "https://artifacts.elastic.co/maven",
    libraryDependencies ++= Seq(
      elastic4sCore,
      elastic4sHttpStreams,
      elastic4sClient,
//      jts,
      ehcache,
      scopt,
      specs % Test
    ),
    normalizedName := "migrate",
    Compile / mainClass := Some("org.thp.thehive.migration.Migrate"),
    Universal / mappings := {
      val backendMappings = (thehiveBackend / Universal / mappings).value.map(_._2).toSet
      (Universal / mappings).value.filterNot(m => backendMappings.contains(m._2)) :+
        (ThisBuild / baseDirectory).value / "package" / "logback-migration.xml" -> "conf/logback-migration.xml"
    },
    linuxPackageMappings := {
      linuxPackageMappings.value.map { packageMappings =>
        val newMapping = packageMappings
          .mappings
          .filterNot(_._2.startsWith("/opt/thehive4-migration/conf"))
          .map(remapPath("thehive4-migration", "thehive", "/opt"))
        LinuxPackageMapping(newMapping, packageMappings.fileData)
      } :+
        LinuxPackageMapping(Seq((ThisBuild / baseDirectory).value / "package" / "logback-migration.xml" -> "/etc/thehive/logback-migration.xml"))
          .withPerms("644")
          .withConfig()
    },
    Debian / linuxPackageMappings := linuxPackageMappings.value,
    Debian / debianPackageDependencies ++= Seq("java8-runtime-headless", s"thehive4-backend (= ${version.value})"),
    rpmRequirements ++= Seq("java-1.8.0-openjdk-headless", s"thehive4-backend = ${version.value}")
  )
