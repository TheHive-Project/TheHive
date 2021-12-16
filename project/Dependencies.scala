import sbt._

object Dependencies {
  val janusVersion        = "0.5.3"
  val akkaVersion: String = play.core.PlayVersion.akkaVersion
  val elastic4sVersion    = "7.10.2"

  lazy val specs                   = "com.typesafe.play"        %% "play-specs2"                        % play.core.PlayVersion.current
  lazy val playLogback             = "com.typesafe.play"        %% "play-logback"                       % play.core.PlayVersion.current
  lazy val playGuice               = "com.typesafe.play"        %% "play-guice"                         % play.core.PlayVersion.current
  lazy val playFilters             = "com.typesafe.play"        %% "filters-helpers"                    % play.core.PlayVersion.current
  lazy val playMockws              = "de.leanovate.play-mockws" %% "play-mockws"                        % "2.8.0"
  lazy val akkaActor               = "com.typesafe.akka"        %% "akka-actor"                         % akkaVersion
  lazy val akkaCluster             = "com.typesafe.akka"        %% "akka-cluster"                       % akkaVersion
  lazy val akkaClusterTools        = "com.typesafe.akka"        %% "akka-cluster-tools"                 % akkaVersion
  lazy val akkaClusterTyped        = "com.typesafe.akka"        %% "akka-cluster-typed"                 % akkaVersion
  lazy val akkaHttp                = "com.typesafe.akka"        %% "akka-http"                          % play.core.PlayVersion.akkaHttpVersion
  lazy val akkaHttpXml             = "com.typesafe.akka"        %% "akka-http-xml"                      % play.core.PlayVersion.akkaHttpVersion
  lazy val janusGraph              = "org.janusgraph"            % "janusgraph"                         % janusVersion
  lazy val janusGraphCore          = "org.janusgraph"            % "janusgraph-core"                    % janusVersion
  lazy val janusGraphBerkeleyDB    = "org.janusgraph"            % "janusgraph-berkeleyje"              % janusVersion
  lazy val janusGraphLucene        = "org.janusgraph"            % "janusgraph-lucene"                  % janusVersion
  lazy val janusGraphElasticSearch = "org.janusgraph"            % "janusgraph-es"                      % janusVersion
  lazy val janusGraphCassandra     = "org.janusgraph"            % "janusgraph-cql"                     % janusVersion
  lazy val janusGraphInMemory      = "org.janusgraph"            % "janusgraph-inmemory"                % janusVersion
  lazy val tinkerpop               = "org.apache.tinkerpop"      % "gremlin-core"                       % "3.4.6" // align with janusgraph
  lazy val scalactic               = "org.scalactic"            %% "scalactic"                          % "3.2.3"
  lazy val scalaGuice              = "net.codingwell"           %% "scala-guice"                        % "4.2.11"
  lazy val shapeless               = "com.chuusai"              %% "shapeless"                          % "2.3.3"
  lazy val bouncyCastle            = "org.bouncycastle"          % "bcprov-jdk15on"                     % "1.68"
  lazy val apacheConfiguration     = "commons-configuration"     % "commons-configuration"              % "1.10"
  lazy val macroParadise           = "org.scalamacros"           % "paradise"                           % "2.1.1" cross CrossVersion.full
  lazy val chimney                 = "io.scalaland"             %% "chimney"                            % "0.6.1"
  lazy val elastic4sCore           = "com.sksamuel.elastic4s"   %% "elastic4s-core"                     % elastic4sVersion
  lazy val elastic4sHttpStreams    = "com.sksamuel.elastic4s"   %% "elastic4s-http-streams"             % elastic4sVersion
  lazy val elastic4sClient         = "com.sksamuel.elastic4s"   %% "elastic4s-client-esjava"            % elastic4sVersion
  lazy val reflections             = "org.reflections"           % "reflections"                        % "0.9.12"
  lazy val hadoopClient            = "org.apache.hadoop"         % "hadoop-client"                      % "3.3.0" exclude ("log4j", "log4j")
  lazy val zip4j                   = "net.lingala.zip4j"         % "zip4j"                              % "2.6.4"
  lazy val alpakka                 = "com.lightbend.akka"       %% "akka-stream-alpakka-json-streaming" % "2.0.2"
  lazy val handlebars              = "com.github.jknack"         % "handlebars"                         % "4.2.0"
  lazy val playMailer              = "com.typesafe.play"        %% "play-mailer"                        % "8.0.1"
  lazy val playMailerGuice         = "com.typesafe.play"        %% "play-mailer-guice"                  % "8.0.1"
  lazy val pbkdf2                  = "io.github.nremond"        %% "pbkdf2-scala"                       % "0.6.5"
  lazy val alpakkaS3               = "com.lightbend.akka"       %% "akka-stream-alpakka-s3"             % "2.0.2"
  lazy val commonCodec             = "commons-codec"             % "commons-codec"                      % "1.15"
  lazy val scopt                   = "com.github.scopt"         %% "scopt"                              % "4.0.0"
  lazy val aix                     = "ai.x"                     %% "play-json-extensions"               % "0.42.0"

  def scalaReflect(scalaVersion: String)  = "org.scala-lang" % "scala-reflect"  % scalaVersion
  def scalaCompiler(scalaVersion: String) = "org.scala-lang" % "scala-compiler" % scalaVersion
}
