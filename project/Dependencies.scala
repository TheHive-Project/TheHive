import sbt._

object Dependencies {
  val scalaVersion = "2.12.12"

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

    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.2.6"

    val akkaVersion      = "2.5.31"
    val reflections      = "org.reflections" % "reflections" % "0.9.11"
    val zip4j            = "net.lingala.zip4j" % "zip4j" % "1.3.2"
    val elastic4play     = "org.thehive-project" %% "elastic4play" % "1.11.8"
    val akkaCluster      = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
    val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
    val akkaClusterTyped = "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion
  }
}
