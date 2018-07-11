import Dependencies._

resolvers += "elastic" at "https://artifacts.elastic.co/maven"

libraryDependencies ++= Seq(
  Library.Play.cache,
  Library.Play.ws,
  Library.Play.ahc,
  Library.Play.filters,
  Library.Play.guice,
  Library.scalaGuice,
  Library.elastic4play,
  Library.zip4j,
  Library.reflections,
  Library.play2pdf
)

play.sbt.routes.RoutesKeys.routesImport -= "controllers.Assets.Asset"
