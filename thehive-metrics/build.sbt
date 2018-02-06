import Dependencies._

libraryDependencies ++= Seq(
  Library.Play.cache,
  Library.Play.ws,
  Library.scalaGuice,
  Library.elastic4play,
  Library.reflections,
  "io.dropwizard.metrics" % "metrics-core" % "3.1.2",
  "io.dropwizard.metrics" % "metrics-json" % "3.1.2",
  "io.dropwizard.metrics" % "metrics-jvm" % "3.1.2",
  "io.dropwizard.metrics" % "metrics-logback" % "3.1.2",
  "io.dropwizard.metrics" % "metrics-graphite" % "3.1.2",
  "io.dropwizard.metrics" % "metrics-ganglia" % "3.1.2",
  "info.ganglia.gmetric4j" % "gmetric4j" % "1.0.10"
)
