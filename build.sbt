import Dependencies._
import com.typesafe.sbt.packager.Keys.bashScriptDefines
import org.thp.ghcl.Milestone

val thehiveVersion         = "4.1.7-1"
val scala212               = "2.12.13"
val scala213               = "2.13.1"
val supportedScalaVersions = List(scala212, scala213)

organization in ThisBuild := "org.thp"
scalaVersion in ThisBuild := scala212
crossScalaVersions in ThisBuild := supportedScalaVersions
resolvers in ThisBuild ++= Seq(
  Resolver.mavenLocal,
  "Oracle Released Java Packages" at "https://download.oracle.com/maven",
  "TheHive project repository" at "https://dl.bintray.com/thehive-project/maven/"
)
scalacOptions in ThisBuild ++= Seq(
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
)
fork in Test in ThisBuild := true
javaOptions in Test in ThisBuild += s"-Dlogger.file=${file("test/resources/logback-test.xml").getAbsoluteFile}"
javaOptions in ThisBuild ++= Seq(
  "-Xms512M",
  "-Xmx2048M",
  "-Xss1M",
  "-XX:+CMSClassUnloadingEnabled",
  "-XX:MaxPermSize=256M",
  "-XX:MaxMetaspaceSize=512m"
)
scalafmtConfig in ThisBuild := file(".scalafmt.conf")
scalacOptions in ThisBuild ++= {
  CrossVersion.partialVersion((Compile / scalaVersion).value) match {
    case Some((2, n)) if n >= 13 => "-Ymacro-annotations" :: Nil
    case _                       => Nil
  }
}
libraryDependencies in ThisBuild ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n >= 13 => Nil
    case _                       => compilerPlugin(macroParadise) :: Nil
  }
}
dependencyOverrides in ThisBuild ++= Seq(
//  "org.locationtech.spatial4j" % "spatial4j"                 % "0.6",
//  "org.elasticsearch.client" % "elasticsearch-rest-client" % "6.7.2"
  akkaActor
)
PlayKeys.includeDocumentationInBinary := false
milestoneFilter := ((milestone: Milestone) => milestone.title.startsWith("4"))

lazy val scalligraph = (project in file("ScalliGraph"))
  .settings(name := "scalligraph")

lazy val thehive = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(thehiveCore, thehiveCortex, thehiveMisp, thehiveFrontend, thehiveMigration)
  .settings(
    name := "thehive",
    version := thehiveVersion,
    crossScalaVersions := Nil,
    PlayKeys.playMonitoredFiles ~= (_.filter(f => f.compareTo(file("frontend/app").getAbsoluteFile) != 0)),
    PlayKeys.devSettings += "play.server.provider" -> "org.thp.thehive.CustomAkkaHttpServerProvider",
//    Universal / mappings ++= (thehiveMigration / Universal / mappings).value,
    Compile / run := {
      (thehiveFrontend / gruntDev).value
      (Compile / run).evaluated
    },
    discoveredMainClasses in Compile := Seq("play.core.server.ProdServerStart", "org.thp.thehive.migration.Migrate", "org.thp.thehive.cloner.Cloner"),
    mainClass in (Compile, bashScriptDefines) := None,
    makeBashScripts ~= {
      _.map {
        case (f, "bin/prod-server-start") => (f, "bin/thehive")
        case other                        => other
      }
    },
    clean := {
      (clean in scalligraph).value
      (clean in thehiveCore).value
      (clean in thehiveDto).value
      (clean in thehiveClient).value
      (clean in thehiveFrontend).value
      (clean in thehiveCortex).value
      (clean in thehiveMisp).value
      (clean in cortexClient).value
      (clean in mispClient).value
      (clean in thehiveMigration).value
      (clean in clientCommon).value
      (clean in cortexDto).value
    },
    test := {
      (test in Test in scalligraph).value
      (test in Test in thehiveCore).value
      (test in Test in thehiveDto).value
      (test in Test in thehiveClient).value
      (test in Test in thehiveFrontend).value
      (test in Test in thehiveCortex).value
      (test in Test in thehiveMisp).value
      (test in Test in cortexClient).value
      (test in Test in mispClient).value
      (test in Test in thehiveMigration).value
      (test in Test in clientCommon).value
      (test in Test in cortexDto).value
    },
    testQuick := {
      (testQuick in Test in scalligraph).evaluated
      (testQuick in Test in thehiveCore).evaluated
      (testQuick in Test in thehiveDto).evaluated
      (testQuick in Test in thehiveClient).evaluated
      (testQuick in Test in thehiveFrontend).evaluated
      (testQuick in Test in thehiveCortex).evaluated
      (testQuick in Test in thehiveMisp).evaluated
      (testQuick in Test in cortexClient).evaluated
      (testQuick in Test in mispClient).evaluated
      (testQuick in Test in thehiveMigration).evaluated
      (testQuick in Test in clientCommon).evaluated
      (testQuick in Test in cortexDto).evaluated
    }
  )

lazy val thehiveCore = (project in file("thehive"))
  .enablePlugins(PlayScala)
  .dependsOn(scalligraph)
  .dependsOn(scalligraph % "test -> test")
  .dependsOn(cortexClient % "test -> test")
  .dependsOn(thehiveDto)
  .dependsOn(clientCommon)
  .dependsOn(thehiveClient % Test)
  .settings(
    name := "thehive-core",
    version := thehiveVersion,
    libraryDependencies ++= Seq(
      chimney,
      guice,
      akkaCluster,
      akkaClusterTyped,
      akkaClusterTools,
      zip4j,
      ws,
      specs % Test,
      handlebars,
      playMailer,
      playMailerGuice,
      pbkdf2,
      commonCodec,
      scalaGuice,
      reflections
    )
  )

lazy val thehiveDto = (project in file("dto"))
  .dependsOn(scalligraph)
  .settings(
    name := "thehive-dto",
    version := thehiveVersion,
    libraryDependencies ++= Seq(
      aix
    )
  )

lazy val thehiveClient = (project in file("client"))
  .dependsOn(thehiveDto)
  .dependsOn(clientCommon)
  .settings(
    name := "thehive-client",
    version := thehiveVersion,
    libraryDependencies ++= Seq(
      ws
    )
  )

lazy val npm        = taskKey[Unit]("Install npm dependencies")
lazy val bower      = taskKey[Unit]("Install bower dependencies")
lazy val gruntDev   = taskKey[Unit]("Inject bower dependencies in index.html")
lazy val gruntBuild = taskKey[Seq[(File, String)]]("Build frontend files")

lazy val thehiveFrontend = (project in file("frontend"))
  .settings(
    name := "thehive-frontend",
    version := thehiveVersion,
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
    watchSources := Nil,
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
    version := thehiveVersion,
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
  .settings(
    name := "thehive-cortex",
    version := thehiveVersion,
    libraryDependencies ++= Seq(
      reflections,
      specs % Test
    )
  )

lazy val cortexDto = (project in file("cortex/dto"))
  .dependsOn(scalligraph)
  .settings(
    name := "cortex-dto",
    version := thehiveVersion,
    libraryDependencies ++= Seq(
      chimney
    )
  )

lazy val cortexClient = (project in file("cortex/client"))
  .dependsOn(cortexDto)
  .dependsOn(clientCommon)
  .dependsOn(scalligraph % "test -> test")
  .settings(
    name := "cortex-client",
    version := thehiveVersion,
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
  .settings(
    name := "thehive-misp",
    version := thehiveVersion,
    libraryDependencies ++= Seq(
      specs      % Test,
      playMockws % Test
    )
  )

lazy val mispClient = (project in file("misp/client"))
  .dependsOn(scalligraph)
  .dependsOn(clientCommon)
  .settings(
    name := "misp-client",
    version := thehiveVersion,
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
  .settings(
    name := "thehive-migration",
    version := thehiveVersion,
    resolvers += "elasticsearch-releases" at "https://artifacts.elastic.co/maven",
    crossScalaVersions := Seq(scala212),
    libraryDependencies ++= Seq(
      elastic4sCore,
      elastic4sHttpStreams,
      elastic4sClient,
//      jts,
      ehcache,
      scopt,
      specs % Test
    ),
    normalizedName := "migrate"
  )

lazy val rpmPackageRelease = (project in file("package/rpm-release"))
  .enablePlugins(RpmPlugin)
  .settings(
    name := "thehive-project-release",
    maintainer := "TheHive Project <support@thehive-project.org>",
    version := "1.2.0",
    rpmRelease := "1",
    rpmVendor := "TheHive Project",
    rpmUrl := Some("http://thehive-project.org/"),
    rpmLicense := Some("AGPL"),
    maintainerScripts in Rpm := Map.empty,
    linuxPackageSymlinks in Rpm := Nil,
    packageSummary := "TheHive-Project RPM repository",
    packageDescription :=
      """This package contains the TheHive-Project packages repository
        |GPG key as well as configuration for yum.""".stripMargin,
    linuxPackageMappings in Rpm := Seq(
      packageMapping(
        file("PGP-PUBLIC-KEY")                       -> "etc/pki/rpm-gpg/GPG-TheHive-Project",
        file("package/rpm-release/thehive-rpm.repo") -> "/etc/yum.repos.d/thehive-rpm.repo",
        file("LICENSE")                              -> "/usr/share/doc/thehive-project-release/LICENSE"
      )
    )
  )
