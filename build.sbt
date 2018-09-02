import Dependencies._

lazy val thehive = (project in file("."))
  .dependsOn(scalligraph, thehiveCore)
  .aggregate(scalligraph, thehiveCore)
  .settings(
    inThisBuild(
      List(
        organization := "org.thp",
        scalaVersion := "2.12.6",
        resolvers += Resolver.mavenLocal,
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
        javaOptions += "-Xmx1G",
        addCompilerPlugin(macroParadise),
        scalafmtConfig := Some(file(".scalafmt.conf"))
      )),
    name := "thehive"
  )

lazy val scalligraph = (project in file("ScalliGraph"))
  .settings(name := "scalligraph")

lazy val thehiveCore = (project in file("thehive"))
  .enablePlugins(PlayScala)
  .dependsOn(scalligraph)
  .dependsOn(scalligraph % "test -> test")
  .settings(
    name := "thehive-core",
    libraryDependencies ++= Seq(
      chimney,
      specs % Test
    ),
  )
