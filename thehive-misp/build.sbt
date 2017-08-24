import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.ws,
  Library.Play.guice,
  Library.Play.ahc,
  Library.zip4j,
  Library.elastic4play
)

enablePlugins(PlayScala)
