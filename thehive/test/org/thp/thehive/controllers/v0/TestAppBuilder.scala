package org.thp.thehive.controllers.v0

import com.typesafe.config.ConfigFactory
import org.thp.scalligraph.ScalligraphApplication
import org.thp.scalligraph.models.Database
import org.thp.thehive._
import org.thp.thehive.services.{TheHiveTestModule, WithTheHiveModule}
import play.api.Configuration

import scala.util.Try

trait TestAppBuilder extends LogFileConfig {
  val databaseName: String = "default"

  def buildTestModule(app: ScalligraphApplication): TheHiveModule = new TheHiveTestModule(app)
  def destroyTheHiveModule(thehiveModule: TheHiveModule): Unit    = ()

  def buildTestModuleV0(app: ScalligraphApplication): TheHiveModuleV0 = new TheHiveModuleV0(app)
  def destroyTheHiveModuleV0(thehiveModuleV0: TheHiveModuleV0): Unit  = ()

  def buildApp(db: Database): TestApplication with WithTheHiveModule with WithTheHiveModuleV0 =
    new TestApplication(db) with WithTheHiveModule with WithTheHiveModuleV0 {
      override lazy val configuration: Configuration = Configuration(
        ConfigFactory.parseString(
          """
            |auth.providers: [
            |  {name: header, userHeader: user}
            |  {name: local}
            |  {name: key}
            |]
            |""".stripMargin
        )
      ).withFallback(TestApplication.appWithoutDatabase.configuration)

      override val thehiveModule: TheHiveModule = buildTestModule(this)
      injectModule(thehiveModule)
      override val thehiveModuleV0: TheHiveModuleV0 = buildTestModuleV0(this)
      injectModule(thehiveModuleV0)
    }

  def destroyApp(app: TestApplication with WithTheHiveModule with WithTheHiveModuleV0): Unit = {
    destroyTheHiveModule(app.thehiveModule)
    destroyTheHiveModuleV0(app.thehiveModuleV0)
  }

  def buildDatabase(db: Database): Try[Unit] = new DatabaseBuilderModule(buildApp(db)).databaseBuilder.build(db)

  def testApp[A](body: TestApplication with WithTheHiveModule with WithTheHiveModuleV0 => A): A =
    JanusDatabaseProvider
      .withDatabase(databaseName, buildDatabase, TestApplication.appWithoutDatabase.actorSystem) { db =>
        val app = buildApp(db)
        val res = body(app)
        destroyApp(app)
        res
      }
      .get
}

trait WithTheHiveModuleV0 {
  val thehiveModuleV0: TheHiveModuleV0
}
