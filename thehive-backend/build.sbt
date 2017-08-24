import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.cache,
  Library.Play.ws,
  Library.Play.ahc,
  Library.Play.filters,
  Library.Play.guice,
  Library.scalaGuice,
  Library.elastic4play,
  Library.zip4j,
  Library.reflections
)

enablePlugins(PlayScala)
