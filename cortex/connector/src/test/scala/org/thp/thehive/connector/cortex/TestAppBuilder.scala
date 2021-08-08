package org.thp.thehive.connector.cortex

import akka.actor.ActorRef
import com.typesafe.config.ConfigFactory
import org.thp.cortex.client.{CortexClient, CortexClientConfig, TestCortexClientProvider}
import org.thp.scalligraph.ScalligraphApplication
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.ConfigItem
import org.thp.thehive._
import org.thp.thehive.connector.cortex.services.{CortexActor, CortexTag}
import org.thp.thehive.services.{TheHiveTestModule, WithTheHiveModule}
import play.api.Configuration

import scala.util.Try

trait TestAppBuilder extends LogFileConfig {
  val databaseName: String = "thehiveCortex"

  def buildTheHiveModule(app: ScalligraphApplication): TheHiveModule = new TheHiveTestModule(app)
  def destroyTheHiveModule(thehiveModule: TheHiveModule): Unit       = ()

  def buildCortexModule(app: ScalligraphApplication): CortexTestModule = new CortexTestModule(app)
  def destroyCortexModule(cortexModule: CortexTestModule): Unit        = ()

  def buildDatabase(db: Database): Try[Unit] = new DatabaseBuilderModule(buildApp(db)).databaseBuilder.build(db)

  def buildApp(db: Database): TestApplication with WithTheHiveModule with WithCortexModule =
    new TestApplication(db) with WithTheHiveModule with WithCortexModule {
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
      ).withFallback(TestApplication.configuration)

      override val thehiveModule: TheHiveModule = buildTheHiveModule(this)
      injectModule(thehiveModule)
      override val cortexModule: CortexTestModule = buildCortexModule(this)
      injectModule(cortexModule)
    }

  def destroyApp(app: TestApplication with WithTheHiveModule with WithCortexModule): Unit = {
    destroyTheHiveModule(app.thehiveModule)
    destroyCortexModule(app.cortexModule)
    app.terminate()
  }

  def testApp[A](body: TestApplication with WithTheHiveModule with WithCortexModule => A): A =
    JanusDatabaseProvider
      .withDatabase(databaseName, buildDatabase, TestApplication.actorSystemForDB) { db =>
        val app = buildApp(db)
        try body(app)
        finally destroyApp(app)
      }
      .get
}

trait WithCortexModule {
  val cortexModule: CortexTestModule
}

class CortexTestModule(app: ScalligraphApplication) extends CortexModule(app) {
  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._

  val thehiveModule: TheHiveModule = app.getModule[TheHiveModule]

  lazy val cortexClient: CortexClient                                                     = wire[TestCortexClientProvider].get
  override lazy val clientsConfig: ConfigItem[Seq[CortexClientConfig], Seq[CortexClient]] = TestConfigItem(Seq(cortexClient))
  override lazy val cortexActor: ActorRef @@ CortexTag                                    = wireAnonymousActor[CortexActor].taggedWith[CortexTag]
}
