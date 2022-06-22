import sbt._

object Dependencies {
  val scalaVersion = "2.12.16"

  object Library {

    object Play {
      val ws      = "com.typesafe.play" %% "play-ws"         % play.core.PlayVersion.current
      val ahc     = "com.typesafe.play" %% "play-ahc-ws"     % play.core.PlayVersion.current
      val cache   = "com.typesafe.play" %% "play-ehcache"    % play.core.PlayVersion.current
      val test    = "com.typesafe.play" %% "play-test"       % play.core.PlayVersion.current
      val specs2  = "com.typesafe.play" %% "play-specs2"     % play.core.PlayVersion.current
      val filters = "com.typesafe.play" %% "filters-helpers" % play.core.PlayVersion.current
      val guice   = "com.typesafe.play" %% "play-guice"      % play.core.PlayVersion.current
    }

    val scalaGuice = "net.codingwell" %% "scala-guice" % "5.1.0"

    val reflections      = "org.reflections"     % "reflections"         % "0.10.2"
    val zip4j            = "net.lingala.zip4j"   % "zip4j"               % "2.10.0"
    val elastic4play     = "org.thehive-project" %% "elastic4play"       % "1.13.5"
    val akkaCluster      = "com.typesafe.akka"   %% "akka-cluster"       % play.core.PlayVersion.akkaVersion
    val akkaClusterTyped = "com.typesafe.akka"   %% "akka-cluster-typed" % play.core.PlayVersion.akkaVersion
    val akkaClusterTools = "com.typesafe.akka"   %% "akka-cluster-tools" % play.core.PlayVersion.akkaVersion
  }
}
