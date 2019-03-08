import Dependencies._

lazy val thehive = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveCore)
  .aggregate(scalligraph, thehiveCore, thehiveDto, thehiveClient, thehiveMigration)
  .settings(
    inThisBuild(
      List(
        organization := "org.thp",
        scalaVersion := "2.12.8",
        resolvers ++= Seq(
          Resolver.mavenLocal,
          "Oracle Released Java Packages" at "http://download.oracle.com/maven",
          "TheHive project repository" at "https://dl.bintray.com/thehive-project/maven/"
        ),
        scalacOptions ++= Seq(
          "-encoding",
          "UTF-8",
          "-deprecation",            // Emit warning and location for usages of deprecated APIs.
          "-feature",                // Emit warning and location for usages of features that should be imported explicitly.
          "-unchecked",              // Enable additional warnings where generated code depends on assumptions.
          //"-Xfatal-warnings",      // Fail the compilation if there are any warnings.
          "-Xlint",                  // Enable recommended additional warnings.
          "-Ywarn-adapted-args",     // Warn if an argument list is modified to match the receiver.
          //"-Ywarn-dead-code",      // Warn when dead code is identified.
          "-Ywarn-inaccessible",     // Warn about inaccessible types in method signatures.
          "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
          "-Ywarn-numeric-widen",    // Warn when numerics are widened.
          "-Ywarn-value-discard",    // Warn when non-Unit expression results are unused
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
        addCompilerPlugin(macroParadise),
        scalafmtConfig := Some(file(".scalafmt.conf"))
      )),
    name := "thehive",
//    PlayKeys.externalizeResources := false
    compile := {
      scala.sys.process.Process(Seq("grunt", "wiredep"), baseDirectory.value / "frontend").!
      (compile in Compile).value
    },
//    mappings in packageBin in Assets ++= {
//      val dir = baseDirectory.value / "frontend" / "dist"
//      dir ** AllPassFilter pair Path.rebase(dir, "frontend")
//    },
//    resourceManaged in Compile := baseDirectory.value / "frontend"
//    resourceDirectories := Seq(baseDirectory.value / "frontend")
//      mappings in Universal ++= directory(baseDirectory.value / "public")
//    resourceDirectories in Compile += baseDirectory.value / "frontend",
//    unmanagedResourceDirectories in Compile += baseDirectory.value / "frontend"
  )

lazy val scalligraph = (project in file("ScalliGraph"))
  .settings(name := "scalligraph")

lazy val thehiveCore = (project in file("thehive"))
  .enablePlugins(PlayScala)
  .dependsOn(scalligraph)
  .dependsOn(scalligraph % "test -> test")
  .dependsOn(thehiveDto)
  .dependsOn(thehiveClient % Test)
  .settings(
    name := "thehive-core",
    libraryDependencies ++= Seq(
      chimney,
      guice,
      reflections,
      ws    % Test,
      specs % Test
    )
  )

lazy val thehiveDto = (project in file("dto"))
  .dependsOn(scalligraph)
  .settings(
    name := "thehive-dto",
    libraryDependencies ++= Seq(
      chimney
    )
  )

lazy val thehiveClient = (project in file("client"))
  .dependsOn(scalligraph)
  .dependsOn(thehiveDto)
  .settings(
    name := "thehive-client",
    libraryDependencies ++= Seq(
      ws
    )
  )

lazy val thehiveMigration = (project in file("migration"))
  .dependsOn(scalligraph)
  .dependsOn(thehiveCore)
  .settings(
    name := "thehive-migration",
    libraryDependencies ++= Seq(
      elastic4play,
      ehcache,
      specs % Test,
    ),
    dependencyOverrides += "org.locationtech.spatial4j" % "spatial4j" % "0.6",
    resourceDirectory in Compile := baseDirectory.value / ".." / "conf",
    fork := true,
    javaOptions := Seq(/*"-Dlogback.debug=true", */"-Dlogger.file=../conf/migration-logback.xml"),
  )