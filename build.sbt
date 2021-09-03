import Dependencies._
import org.thp.ghcl.Milestone
import sbt.file

val defaultSettings = Seq(
  version := "4.2.0-1-SNAPSHOT",
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
  Test / javaOptions += s"-Dlogger.file=${file("test/resources/logback-test.xml").getAbsoluteFile}",
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

milestoneFilter := ((milestone: Milestone) => milestone.title.startsWith("4"))

lazy val scalligraphRoot = ProjectRef(file("scalligraph"), "scalligraphRoot")

lazy val scalligraph = ProjectRef(file("scalligraph"), "scalligraph")

lazy val scalligraphTest = ProjectRef(file("scalligraph"), "scalligraphTest")

lazy val scalligraphJanusgraph = ProjectRef(file("scalligraph"), "scalligraphJanusgraph")

lazy val dockerTags = taskKey[Seq[String]]("Get the list of docker tags")
lazy val writeTags  = taskKey[File]("Write a '.tags' file containing the list of tags")

lazy val thehiveRoot = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(DockerPlugin)
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
  .settings(
    name := "root",
    PlayKeys.playMonitoredFiles ~= (_.filter(f => f.compareTo(file("frontend").getAbsoluteFile) != 0)),
    run / fork := true,
    run := {
      (thehiveFrontend / gruntDev).value
      (Compile / runMain).toTask(" org.thp.thehive.TheHiveStarter . --dev").value
    },
    changeLog / aggregate := false
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
  .settings(
    name := "thehive",
    PlayKeys.playMonitoredFiles ~= (_.filter(f => f.compareTo(file("frontend/app").getAbsoluteFile) != 0)),
    PlayKeys.devSettings += "play.server.provider" -> "org.thp.thehive.CustomAkkaHttpServerProvider"
  )

lazy val thehiveBackend = (project in file("target/thehive-backend"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(thehiveCore, thehiveMisp, thehiveCortex)
  .aggregate(thehiveCore, thehiveMisp, thehiveCortex)
  .settings(defaultSettings)
  .settings(
    name := "thehive-backend",
    Compile / mainClass := Some("org.thp.thehive.TheHiveStarter")
  )

lazy val thehiveCore = (project in file("thehive"))
  .enablePlugins(PlayScala)
  .dependsOn(scalligraph, scalligraphJanusgraph, scalligraphTest % "test -> test")
  .dependsOn(thehiveDto, clientCommon, /* cortexClient % "test -> test",*/ thehiveClient % Test)
  .aggregate(thehiveDto, thehiveClient)
  .settings(defaultSettings)
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
      macWireProxy,
      passay
    )
  )

lazy val thehiveDto = (project in file("dto"))
  .dependsOn(scalligraph) // required for parsers
  .settings(defaultSettings)
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
  .settings(
    name := "thehive-shell",
    javaOptions := Seq("-XX:+UseG1GC", "-Xmx4G"),
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    run / connectInput := true,
    run / fork := false,
    libraryDependencies ++= Seq(
      ammonite,
      macWireMacros,
      macWireMacrosakka,
      macWireUtil,
      macWireProxy
    ),
    normalizedName := "thehive-shell",
    Compile / mainClass := Some("org.thp.thehive.shell.Shell")
  )

lazy val npm        = taskKey[Unit]("Install npm dependencies")
lazy val bower      = taskKey[Unit]("Install bower dependencies")
lazy val gruntDev   = taskKey[Unit]("Inject bower dependencies in index.html")
lazy val gruntBuild = taskKey[Seq[(File, String)]]("Build frontend files")

lazy val thehiveFrontend = (project in file("frontend"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(scalligraph)
  .settings(defaultSettings)
  .settings(
    name := "thehive-frontend",
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
    Runtime / dependencyClasspath := Nil
  )

lazy val thehiveFrontendJar = (project in file("target/thehive-frontend-jar"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(thehiveFrontend)
  .aggregate(thehiveFrontend)
  .settings(defaultSettings)
  .settings(
    name := "thehive-frontend-jar"
  )

lazy val clientCommon = (project in file("client-common"))
  .dependsOn(scalligraph)
  .settings(defaultSettings)
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
  .settings(
    name := "thehive-migration",
    resolvers += "elasticsearch-releases" at "https://artifacts.elastic.co/maven",
    libraryDependencies ++= Seq(
      elastic4sCore,
      elastic4sHttpStreams,
      elastic4sClient,
//      jts,
      ehcache,
      scopt,
      macWireMacros,
      macWireMacrosakka,
      macWireUtil,
      macWireProxy,
      specs % Test
    ),
    normalizedName := "migrate",
    Compile / mainClass := Some("org.thp.thehive.migration.Migrate")
  )
