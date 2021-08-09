package org.thp.thehive.controllers.v1

import com.typesafe.config.ConfigFactory
import org.thp.scalligraph.ScalligraphApplication
import org.thp.scalligraph.models.Database
import org.thp.thehive._
import org.thp.thehive.services.{TheHiveTestModule, WithTheHiveModule}
import play.api.Configuration

import scala.util.Try

trait TestAppBuilder extends LogFileConfig {
  val databaseName: String = "default"

  def buildTheHiveModule(app: ScalligraphApplication): TheHiveModule = new TheHiveTestModule(app)
  def destroyTheHiveModule(thehiveModule: TheHiveModule): Unit       = ()

  def buildTheHiveModuleV1(app: ScalligraphApplication): TheHiveModuleV1 = new TheHiveModuleV1(app)
  def destroyTheHiveModuleV1(thehiveModuleV1: TheHiveModuleV1): Unit     = ()

  def buildApp(db: Database): TestApplication with WithTheHiveModule with WithTheHiveModuleV1 =
    new TestApplication(db) with WithTheHiveModule with WithTheHiveModuleV1 {
      override lazy val configuration: Configuration = Configuration(
        ConfigFactory.parseString(
          """
            |auth.providers: [
            |  {name: header, userHeader: user}
            |  {name: local}
            |]
            |""".stripMargin
        )
      ).withFallback(TestApplication.configuration)
      override val thehiveModule: TheHiveModule = buildTheHiveModule(this)
      injectModule(thehiveModule)
      override val thehiveModuleV1: TheHiveModuleV1 = buildTheHiveModuleV1(this)
      injectModule(thehiveModuleV1)
    }

  def destroyApp(app: TestApplication with WithTheHiveModule with WithTheHiveModuleV1): Unit = {
    destroyTheHiveModule(app.thehiveModule)
    destroyTheHiveModuleV1(app.thehiveModuleV1)
    app.terminate()
  }

  def buildDatabase(db: Database): Try[Unit] = new DatabaseBuilderModule(buildApp(db)).databaseBuilder.build(db)

  def testApp[A](body: TestApplication with WithTheHiveModule with WithTheHiveModuleV1 => A): A =
    JanusDatabaseProvider
      .withDatabase(databaseName, buildDatabase, TestApplication.actorSystemForDB) { db =>
        val app = buildApp(db)
        try body(app)
        finally destroyApp(app)
      }
      .get
}

trait WithTheHiveModuleV1 {
  val thehiveModuleV1: TheHiveModuleV1
}
