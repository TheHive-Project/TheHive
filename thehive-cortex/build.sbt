import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.ws,
  Library.elastic4play,
  Library.zip4j
)

enablePlugins(PlayScala)
