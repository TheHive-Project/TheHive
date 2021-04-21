package org.thp.thehive.controllers.v0

import com.typesafe.config.ConfigFactory
import org.thp.scalligraph.{ScalligraphApplication, ScalligraphApplicationImpl}
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.{TheHiveTestModule, WithTheHiveModule}
import org.thp.thehive.{DatabaseBuilderModule, JanusDatabaseProvider, LogFileConfig, TestApplication, TheHiveModule}
import play.api.{Configuration, Mode}

import java.io.File
import scala.util.Try

trait TestAppBuilder extends LogFileConfig {
  val databaseName: String      = "default"
  val testApplicationNoDatabase = new ScalligraphApplicationImpl(new File("."), getClass.getClassLoader, Mode.Test)

  def buildTestModule(app: ScalligraphApplication): TheHiveModule = new TheHiveTestModule(app)

  def buildTestModuleV0(app: ScalligraphApplication): TheHiveModuleV0 = new TheHiveModuleV0(app)

  def buildApp(db: Database): TestApplication with WithTheHiveModule with WithTheHiveModuleV0 =
    new TestApplication(db, testApplicationNoDatabase) with WithTheHiveModule with WithTheHiveModuleV0 {
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
      ).withFallback(testApplicationNoDatabase.configuration)

      override val thehiveModule: TheHiveModule = buildTestModule(this)
      injectModule(thehiveModule)
      override val thehiveModuleV0: TheHiveModuleV0 = buildTestModuleV0(this)
      injectModule(thehiveModuleV0)
    }

  def buildDatabase(db: Database): Try[Unit] = new DatabaseBuilderModule(buildApp(db)).databaseBuilder.build(db)

  def testApp[A](body: TestApplication with WithTheHiveModule with WithTheHiveModuleV0 => A): A =
    JanusDatabaseProvider
      .withDatabase(databaseName, buildDatabase, testApplicationNoDatabase.actorSystem) { db =>
        body(buildApp(db))
      }
      .get
}

trait WithTheHiveModuleV0 {
  val thehiveModuleV0: TheHiveModuleV0
}
