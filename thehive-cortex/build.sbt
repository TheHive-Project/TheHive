import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.ws,
  Library.elastic4play
)

enablePlugins(PlayScala)
