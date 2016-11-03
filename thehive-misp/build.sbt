import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.ws,
  Library.zip4j,
  Library.elastic4play
)

enablePlugins(PlayScala)
