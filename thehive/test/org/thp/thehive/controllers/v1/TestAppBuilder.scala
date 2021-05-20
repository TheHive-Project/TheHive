package org.thp.thehive.controllers.v1

import com.typesafe.config.ConfigFactory
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.{ScalligraphApplication, ScalligraphApplicationImpl}
import org.thp.thehive.services.{TheHiveTestModule, WithTheHiveModule}
import org.thp.thehive._
import play.api.{Configuration, Mode}

import java.io.File
import scala.util.Try

trait TestAppBuilder extends LogFileConfig {
  val databaseName: String      = "default"
  val testApplicationNoDatabase = new ScalligraphApplicationImpl(new File("."), getClass.getClassLoader, Mode.Test)

  def buildTheHiveModule(app: ScalligraphApplication): TheHiveModule = new TheHiveTestModule(app)

  def buildTheHiveModuleV1(app: ScalligraphApplication): TheHiveModuleV1 = new TheHiveModuleV1(app)

  def buildApp(db: Database): TestApplication with WithTheHiveModule with WithTheHiveModuleV1 =
    new TestApplication(db, testApplicationNoDatabase) with WithTheHiveModule with WithTheHiveModuleV1 {
      override lazy val configuration: Configuration = Configuration(
        ConfigFactory.parseString(
          """
            |auth.providers: [
            |  {name: header, userHeader: user}
            |  {name: local}
            |]
            |""".stripMargin
        )
      ).withFallback(testApplicationNoDatabase.configuration)
      override val thehiveModule: TheHiveModule = buildTheHiveModule(this)
      injectModule(thehiveModule)
      override val thehiveModuleV1: TheHiveModuleV1 = buildTheHiveModuleV1(this)
      injectModule(thehiveModuleV1)
    }

  def buildDatabase(db: Database): Try[Unit] = new DatabaseBuilderModule(buildApp(db)).databaseBuilder.build(db)

  def testApp[A](body: TestApplication with WithTheHiveModule with WithTheHiveModuleV1 => A): A =
    JanusDatabaseProvider
      .withDatabase(databaseName, buildDatabase, testApplicationNoDatabase.actorSystem) { db =>
        body(buildApp(db))
      }
      .get

}
trait WithTheHiveModuleV1 {
  val thehiveModuleV1: TheHiveModuleV1
}
