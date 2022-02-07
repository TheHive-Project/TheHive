import sbt._

object Dependencies {
  val janusVersion        = "0.5.3"
  val akkaVersion: String = play.core.PlayVersion.akkaVersion
  val macWireVersion      = "2.4.1"

  lazy val specs                   = "com.typesafe.play"          %% "play-specs2"                        % play.core.PlayVersion.current
  lazy val playLogback             = "com.typesafe.play"          %% "play-logback"                       % play.core.PlayVersion.current
  lazy val playGuice               = "com.typesafe.play"          %% "play-guice"                         % play.core.PlayVersion.current
  lazy val playFilters             = "com.typesafe.play"          %% "filters-helpers"                    % play.core.PlayVersion.current
  lazy val logbackClassic          = "ch.qos.logback"              % "logback-classic"                    % "1.2.8"
  lazy val playMockws              = "de.leanovate.play-mockws"   %% "play-mockws"                        % "2.8.1"
  lazy val akkaActor               = "com.typesafe.akka"          %% "akka-actor"                         % akkaVersion
  lazy val akkaCluster             = "com.typesafe.akka"          %% "akka-cluster"                       % akkaVersion
  lazy val akkaClusterTools        = "com.typesafe.akka"          %% "akka-cluster-tools"                 % akkaVersion
  lazy val akkaClusterTyped        = "com.typesafe.akka"          %% "akka-cluster-typed"                 % akkaVersion
  lazy val akkaHttp                = "com.typesafe.akka"          %% "akka-http"                          % play.core.PlayVersion.akkaHttpVersion
  lazy val akkaHttpXml             = "com.typesafe.akka"          %% "akka-http-xml"                      % play.core.PlayVersion.akkaHttpVersion
  lazy val janusGraph              = "org.janusgraph"              % "janusgraph"                         % janusVersion
  lazy val janusGraphCore          = "org.janusgraph"              % "janusgraph-core"                    % janusVersion
  lazy val janusGraphBerkeleyDB    = "org.janusgraph"              % "janusgraph-berkeleyje"              % janusVersion
  lazy val janusGraphLucene        = "org.janusgraph"              % "janusgraph-lucene"                  % janusVersion
  lazy val janusGraphElasticSearch = "org.janusgraph"              % "janusgraph-es"                      % janusVersion
  lazy val janusGraphCassandra     = "org.janusgraph"              % "janusgraph-cql"                     % janusVersion
  lazy val janusGraphInMemory      = "org.janusgraph"              % "janusgraph-inmemory"                % janusVersion
  lazy val tinkerpop               = "org.apache.tinkerpop"        % "gremlin-core"                       % "3.4.6"         // align with janusgraph
  lazy val scalactic               = "org.scalactic"              %% "scalactic"                          % "3.2.9"
  lazy val shapeless               = "com.chuusai"                %% "shapeless"                          % "2.3.7"
  lazy val bouncyCastle            = "org.bouncycastle"            % "bcprov-jdk15on"                     % "1.69"
  lazy val apacheConfiguration     = "commons-configuration"       % "commons-configuration"              % "1.10"
  lazy val chimney                 = "io.scalaland"               %% "chimney"                            % "0.6.1"
  lazy val reflections             = "org.reflections"             % "reflections"                        % "0.9.12"
  lazy val hadoopClient            = "org.apache.hadoop"           % "hadoop-client"                      % "3.3.1" exclude ("log4j", "log4j")
  lazy val zip4j                   = "net.lingala.zip4j"           % "zip4j"                              % "2.9.0"
  lazy val alpakka                 = "com.lightbend.akka"         %% "akka-stream-alpakka-json-streaming" % "3.0.3"
  lazy val handlebars              = "com.github.jknack"           % "handlebars"                         % "4.2.0"
  lazy val playMailer              = "com.typesafe.play"          %% "play-mailer"                        % "8.0.1"
  lazy val playMailerGuice         = "com.typesafe.play"          %% "play-mailer-guice"                  % "8.0.1"
  lazy val pbkdf2                  = "io.github.nremond"          %% "pbkdf2-scala"                       % "0.6.5"
  lazy val alpakkaS3               = "com.lightbend.akka"         %% "akka-stream-alpakka-s3"             % "3.0.3"
  lazy val commonCodec             = "commons-codec"               % "commons-codec"                      % "1.15"
  lazy val scopt                   = "com.github.scopt"           %% "scopt"                              % "4.0.1"
  lazy val aix                     = "ai.x"                       %% "play-json-extensions"               % "0.42.0"
  lazy val macWireMacros           = "com.softwaremill.macwire"   %% "macros"                             % macWireVersion % "provided"
  lazy val macWireMacrosakka       = "com.softwaremill.macwire"   %% "macrosakka"                         % macWireVersion % "provided"
  lazy val macWireUtil             = "com.softwaremill.macwire"   %% "util"                               % macWireVersion
  lazy val macWireProxy            = "com.softwaremill.macwire"   %% "proxy"                              % macWireVersion
  lazy val ammonite                = "com.lihaoyi"                 % "ammonite"                           % "2.4.0-14-4824b429" cross CrossVersion.full
  lazy val refined                 = "eu.timepit"                 %% "refined"                            % "0.9.27"
  lazy val playJsonRefined         = "de.cbley"                   %% "play-json-refined"                  % "0.8.0"
  lazy val playRefined             = "be.venneborg"               %% "play27-refined"                     % "0.6.0"
  lazy val passay                  = "org.passay"                  % "passay"                             % "1.6.1"
  lazy val bloomFilter             = "com.github.alexandrnikitin" %% "bloom-filter"                       % "0.13.1"
  lazy val scalaParallelCollection = "org.scala-lang.modules"     %% "scala-parallel-collections"         % "1.0.0"
  lazy val quartzScheduler         = "org.quartz-scheduler"        % "quartz"                             % "2.3.2"

  def scalaReflect(scalaVersion: String)  = "org.scala-lang" % "scala-reflect"  % scalaVersion
  def scalaCompiler(scalaVersion: String) = "org.scala-lang" % "scala-compiler" % scalaVersion
}
