import sbt._

object Dependencies {
  val scalaVersion = "2.12.6"

  object Library {

    object Play {
      val version = play.core.PlayVersion.current
      val ws = "com.typesafe.play" %% "play-ws" % version
      val ahc = "com.typesafe.play" %% "play-ahc-ws" % version
      val cache = "com.typesafe.play" %% "play-ehcache" % version
      val test = "com.typesafe.play" %% "play-test" % version
      val specs2 = "com.typesafe.play" %% "play-specs2" % version
      val filters = "com.typesafe.play" %% "filters-helpers" % version
      val guice = "com.typesafe.play" %% "play-guice" % version
    }

    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.2.1"

    val reflections = "org.reflections" % "reflections" % "0.9.11"
    val zip4j = "net.lingala.zip4j" % "zip4j" % "1.3.2"
    val elastic4play = "org.thehive-project" %% "elastic4play" % "1.6.3"
    val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % "2.5.11"
    val akkaClusterTools = "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.11"
  }
}
