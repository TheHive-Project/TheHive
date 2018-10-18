import sbt._

object Dependencies {
  lazy val scalaTest                      = "org.scalatest"         %% "scalatest"               % "3.0.4"
  lazy val gremlinScala                   = "com.michaelpollmeier"  %% "gremlin-scala"           % "3.3.3.4"
  lazy val gremlinOrientdb                = "com.orientechnologies" % "orientdb-gremlin"         % "3.0.4"
  lazy val janusGraph                     = "org.janusgraph"        % "janusgraph-core"          % "0.3.0"
  lazy val scalactic                      = "org.scalactic"         %% "scalactic"               % "3.0.5"
  lazy val specs                          = "com.typesafe.play"     %% "play-specs2"             % play.core.PlayVersion.current
  lazy val scalaGuice                     = "net.codingwell"        %% "scala-guice"             % "4.2.0"
  lazy val sangria                        = "org.sangria-graphql"   %% "sangria"                 % "1.4.2"
  lazy val sangriaPlay                    = "org.sangria-graphql"   %% "sangria-play-json"       % "1.0.4"
  lazy val shapeless                      = "com.chuusai"           %% "shapeless"               % "2.3.3"
  lazy val bouncyCastle                   = "org.bouncycastle"      % "bcprov-jdk15on"           % "1.58"
  lazy val neo4jGremlin                   = "org.apache.tinkerpop"  % "neo4j-gremlin"            % "3.3.3"
  lazy val neo4jTinkerpop                 = "org.neo4j"             % "neo4j-tinkerpop-api-impl" % "0.7-3.2.3" exclude ("org.slf4j", "slf4j-nop")
  lazy val apacheConfiguration            = "commons-configuration" % "commons-configuration"    % "1.10"
  lazy val macroParadise                  = "org.scalamacros"       % "paradise"                 % "2.1.0" cross CrossVersion.full
  lazy val playLogback                    = "com.typesafe.play"     %% "play-logback"            % play.core.PlayVersion.current
  lazy val playGuice                      = "com.typesafe.play"     %% "play-guice"              % play.core.PlayVersion.current
  lazy val chimney                        = "io.scalaland"          %% "chimney"                 % "0.2.1"
  def scalaReflect(scalaVersion: String)  = "org.scala-lang"        % "scala-reflect"            % scalaVersion
  def scalaCompiler(scalaVersion: String) = "org.scala-lang"        % "scala-compiler"           % scalaVersion
}
