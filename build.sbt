import Dependencies._

lazy val scala212               = "2.12.8"
lazy val scala213               = "2.13.0"
lazy val supportedScalaVersions = List(scala212, scala213)
lazy val commonSettings = Seq(
  organization := "org.thp",
  scalaVersion := scala212,
  crossScalaVersions := supportedScalaVersions,
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "Oracle Released Java Packages" at "http://download.oracle.com/maven",
    "TheHive project repository" at "https://dl.bintray.com/thehive-project/maven/"
  ),
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
    "-Xprint-types"
  ),
  fork in Test := true,
  //        javaOptions += "-Xmx1G",
  scalafmtConfig := file(".scalafmt.conf"),
  scalacOptions ++= {
    CrossVersion.partialVersion((Compile / scalaVersion).value) match {
      case Some((2, n)) if n >= 13 => "-Ymacro-annotations" :: Nil
      case _                       => Nil
    }
  },
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, n)) if n >= 13 => Nil
      case _                       => compilerPlugin(macroParadise) :: Nil
    }
  }
)

lazy val scalligraph = (project in file("ScalliGraph"))
  .settings(name := "scalligraph")

lazy val thehive = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveCore, thehiveCortex, thehiveMisp)
  .aggregate(scalligraph, thehiveCore, thehiveDto, thehiveClient, thehiveFrontend, thehiveCortex, thehiveMisp, cortexClient, mispClient)
  .settings(commonSettings)
  .settings(
    name := "thehive",
    crossScalaVersions := Nil,
    Compile / run := {
      (thehiveFrontend / gruntDev).value
      (Compile / run).evaluated
    }
  )

lazy val thehiveCore = (project in file("thehive"))
  .enablePlugins(PlayScala)
  .dependsOn(scalligraph)
  .dependsOn(scalligraph % "test -> test")
  .dependsOn(cortexClient % "test -> test")
  .dependsOn(thehiveDto)
  .dependsOn(thehiveClient % Test)
  .settings(commonSettings)
  .settings(
    name := "thehive-core",
    libraryDependencies ++= Seq(
      chimney,
      guice,
      akkaCluster,
      akkaClusterTools,
      zip4j,
      ws    % Test,
      specs % Test
    )
  )

lazy val thehiveDto = (project in file("dto"))
  .dependsOn(scalligraph)
  .settings(commonSettings)
  .settings(
    name := "thehive-dto"
  )

lazy val thehiveClient = (project in file("client"))
  .dependsOn(thehiveDto)
  .dependsOn(clientCommon)
  .settings(commonSettings)
  .settings(
    name := "thehive-client",
    libraryDependencies ++= Seq(
      ws
    )
  )

lazy val npm        = taskKey[Unit]("Install npm dependencies")
lazy val bower      = taskKey[Unit]("Install bower dependencies")
lazy val gruntDev   = taskKey[Unit]("Inject bower dependencies in index.html")
lazy val gruntBuild = taskKey[Seq[(File, String)]]("Build frontend files")

lazy val thehiveFrontend = (project in file("frontend"))
  .settings(commonSettings)
  .settings(
    name := "thehive-frontend",
    npm :=
      FileBuilder(
        label = "npm",
        inputFiles = baseDirectory.value / "package.json",
        outputFiles = baseDirectory.value / "node_modules" ** AllPassFilter,
        command = baseDirectory.value -> "npm install",
        streams = streams.value
      ),
    bower := FileBuilder(
      label = "bower",
      inputFiles = baseDirectory.value / "bower.json",
      outputFiles = baseDirectory.value / "bower_components" ** AllPassFilter,
      command = baseDirectory.value -> "bower install",
      streams = streams.value
    ),
    gruntDev := {
      npm.value
      bower.value
      FileBuilder(
        label = "grunt",
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
      val outputFiles = FileBuilder(
        label = "grunt",
        inputFiles = baseDirectory.value / "bower_components" ** AllPassFilter,
        outputFiles = dist ** AllPassFilter,
        command = baseDirectory.value -> "grunt build",
        streams = streams.value
      )
      for {
        file        <- outputFiles.toSeq
        rebasedFile <- sbt.Path.rebase(dist, "frontend")(file)
      } yield file -> rebasedFile
    },
    Compile / resourceDirectory := baseDirectory.value / "app",
    Compile / packageBin / mappings := gruntBuild.value,
    cleanFiles ++= Seq(
      baseDirectory.value / "dist",
      baseDirectory.value / "bower_components",
      baseDirectory.value / "node_modules"
    )
  )

lazy val clientCommon = (project in file("client-common"))
  .dependsOn(scalligraph)
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
  .dependsOn(scalligraph % "test -> test")
  .settings(commonSettings)
  .settings(
    name := "thehive-cortex",
    libraryDependencies ++= Seq(
      specs % Test
    )
  )

lazy val cortexDto = (project in file("cortex/dto"))
  .dependsOn(scalligraph)
  .settings(commonSettings)
  .settings(
    name := "cortex-dto",
    libraryDependencies ++= Seq(
      chimney
    )
  )

lazy val cortexClient = (project in file("cortex/client"))
  .dependsOn(cortexDto)
  .dependsOn(clientCommon)
  .dependsOn(scalligraph % "test -> test")
  .settings(commonSettings)
  .settings(
    name := "cortex-client",
    libraryDependencies ++= Seq(
      ws,
      specs       % Test,
      playFilters % Test,
      playMockws  % Test
    )
  )

lazy val thehiveMisp = (project in file("misp/connector"))
  .dependsOn(thehiveCore)
  .dependsOn(mispClient)
  .dependsOn(thehiveCore % "test -> test")
  .settings(commonSettings)
  .settings(
    name := "thehive-misp",
    libraryDependencies ++= Seq(
      specs      % Test,
      playMockws % Test
    )
  )

lazy val mispClient = (project in file("misp/client"))
  .dependsOn(scalligraph)
  .dependsOn(clientCommon)
  .settings(commonSettings)
  .settings(
    name := "misp-client",
    libraryDependencies ++= Seq(
      ws,
      alpakka,
      specs      % Test,
      playMockws % Test
    )
  )

//lazy val thehiveMigration = (project in file("migration"))
//  .enablePlugins(JavaAppPackaging)
//  .dependsOn(scalligraph)
//  .dependsOn(thehiveCore)
//  .settings(commonSettings)
//  .settings(
//    name := "thehive-migration",
//    resolvers += "elasticsearch-releases" at "https://artifacts.elastic.co/maven",
//    crossScalaVersions := Seq(scala212),
//    libraryDependencies ++= Seq(
//      elastic4play,
//      ehcache,
//      specs % Test
//    ),
//    dependencyOverrides += "org.locationtech.spatial4j" % "spatial4j" % "0.6",
//    resourceDirectory in Compile := baseDirectory.value / ".." / "conf",
//    fork := true,
//    javaOptions := Seq("-Dlogger.file=../conf/migration-logback.xml")
//  )
