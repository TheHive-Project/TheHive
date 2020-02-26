import sbt._

object Dependencies {
  val janusVersion        = "0.4.0"
  val akkaVersion: String = play.core.PlayVersion.akkaVersion
  val elastic4sVersion    = "6.7.4"

  lazy val specs                   = "com.typesafe.play"        %% "play-specs2"                        % play.core.PlayVersion.current
  lazy val playLogback             = "com.typesafe.play"        %% "play-logback"                       % play.core.PlayVersion.current
  lazy val playGuice               = "com.typesafe.play"        %% "play-guice"                         % play.core.PlayVersion.current
  lazy val playFilters             = "com.typesafe.play"        %% "filters-helpers"                    % play.core.PlayVersion.current
  lazy val playMockws              = "de.leanovate.play-mockws" %% "play-mockws"                        % "2.7.1" // FIXME play.core.PlayVersion.current
  lazy val akkaCluster             = "com.typesafe.akka"        %% "akka-cluster"                       % akkaVersion
  lazy val akkaClusterTools        = "com.typesafe.akka"        %% "akka-cluster-tools"                 % akkaVersion
  lazy val akkaClusterTyped        = "com.typesafe.akka"        %% "akka-cluster-typed"                 % akkaVersion
  lazy val akkaHttp                = "com.typesafe.akka"        %% "akka-http"                          % "10.1.11"
  lazy val akkaHttpXml             = "com.typesafe.akka"        %% "akka-http-xml"                      % "10.1.11"
  lazy val janusGraph              = "org.janusgraph"           % "janusgraph-core"                     % janusVersion
  lazy val janusGraphBerkeleyDB    = "org.janusgraph"           % "janusgraph-berkeleyje"               % janusVersion
  lazy val janusGraphHBase         = "org.janusgraph"           % "janusgraph-hbase"                    % janusVersion
  lazy val janusGraphLucene        = "org.janusgraph"           % "janusgraph-lucene"                   % janusVersion
  lazy val janusGraphElasticSearch = "org.janusgraph"           % "janusgraph-es"                       % janusVersion
  lazy val cassandra               = "org.janusgraph"           % "janusgraph-cql"                      % janusVersion
  lazy val gremlinScala            = "com.michaelpollmeier"     %% "gremlin-scala"                      % "3.4.4.3"
  lazy val gremlinOrientdb         = "com.orientechnologies"    % "orientdb-gremlin"                    % "3.0.18"
  lazy val hbaseClient             = "org.apache.hbase"         % "hbase-shaded-client"                 % "1.4.9" exclude ("org.slf4j", "slf4j-log4j12")
  lazy val scalactic               = "org.scalactic"            %% "scalactic"                          % "3.1.0"
  lazy val scalaGuice              = "net.codingwell"           %% "scala-guice"                        % "4.2.6"
  lazy val sangria                 = "org.sangria-graphql"      %% "sangria"                            % "1.4.2"
  lazy val sangriaPlay             = "org.sangria-graphql"      %% "sangria-play-json"                  % "1.0.5"
  lazy val shapeless               = "com.chuusai"              %% "shapeless"                          % "2.3.3"
  lazy val bouncyCastle            = "org.bouncycastle"         % "bcprov-jdk15on"                      % "1.64"
  lazy val neo4jGremlin            = "org.apache.tinkerpop"     % "neo4j-gremlin"                       % "3.3.4"
  lazy val neo4jTinkerpop          = "org.neo4j"                % "neo4j-tinkerpop-api-impl"            % "0.7-3.2.3" exclude ("org.slf4j", "slf4j-nop")
  lazy val apacheConfiguration     = "commons-configuration"    % "commons-configuration"               % "1.10"
  lazy val macroParadise           = "org.scalamacros"          % "paradise"                            % "2.1.1" cross CrossVersion.full
  lazy val chimney                 = "io.scalaland"             %% "chimney"                            % "0.4.0"
  lazy val elastic4play            = "org.thehive-project"      %% "elastic4play"                       % "1.11.5" /*exclude ("org.apache.logging.log4j", "log4j-core") exclude("org.apache.logging.log4j", "log4j-api") exclude("org.apache.logging.log4j", "log4j-1.2-api") */
  lazy val elastic4sCore           = "com.sksamuel.elastic4s"   %% "elastic4s-core"                     % elastic4sVersion
  lazy val elastic4sHttpStreams    = "com.sksamuel.elastic4s"   %% "elastic4s-http-streams"             % elastic4sVersion
  lazy val elastic4sHttp           = "com.sksamuel.elastic4s"   %% "elastic4s-http"                     % elastic4sVersion
  lazy val log4jOverSlf4j          = "org.slf4j"                % "log4j-over-slf4j"                    % "1.7.25"
  lazy val log4jToSlf4j            = "org.apache.logging.log4j" % "log4j-to-slf4j"                      % "2.9.1"
  lazy val reflections             = "org.reflections"          % "reflections"                         % "0.9.12"
  lazy val hadoopClient            = "org.apache.hadoop"        % "hadoop-client"                       % "3.2.1"
  lazy val zip4j                   = "net.lingala.zip4j"        % "zip4j"                               % "2.3.1"
  lazy val alpakka                 = "com.lightbend.akka"       %% "akka-stream-alpakka-json-streaming" % "1.1.2"
  lazy val handlebars              = "com.github.jknack"        % "handlebars"                          % "4.1.2"
  lazy val playMailer              = "com.typesafe.play"        %% "play-mailer"                        % "7.0.1"
  lazy val playMailerGuice         = "com.typesafe.play"        %% "play-mailer-guice"                  % "7.0.1"
  lazy val jts                     = "com.vividsolutions"       % "jts"                                 % "1.13"
  lazy val pbkdf2                  = "io.github.nremond"        %% "pbkdf2-scala"                       % "0.6.5"
  lazy val alpakkaS3               = "com.lightbend.akka"       %% "akka-stream-alpakka-s3"             % "1.1.2"
  lazy val commonCodec             = "commons-codec"            % "commons-codec"                       % "1.11"
  lazy val scopt                   = "com.github.scopt"         %% "scopt"                              % "4.0.0-RC2"

  def scalaReflect(scalaVersion: String)  = "org.scala-lang" % "scala-reflect"  % scalaVersion
  def scalaCompiler(scalaVersion: String) = "org.scala-lang" % "scala-compiler" % scalaVersion
}
