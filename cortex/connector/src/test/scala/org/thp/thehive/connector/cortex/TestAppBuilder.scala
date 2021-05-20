package org.thp.thehive.connector.cortex

import akka.actor.ActorRef
import com.typesafe.config.ConfigFactory
import org.thp.cortex.client.{CortexClient, CortexClientConfig, TestCortexClientProvider}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.ConfigItem
import org.thp.scalligraph.{ScalligraphApplication, ScalligraphApplicationImpl}
import org.thp.thehive.connector.cortex.services.{CortexActor, CortexTag}
import org.thp.thehive.services.{TheHiveTestModule, WithTheHiveModule}
import org.thp.thehive._
import play.api.{Configuration, Mode}

import java.io.File
import scala.util.Try

trait TestAppBuilder extends LogFileConfig {
  val databaseName: String      = "default"
  val testApplicationNoDatabase = new ScalligraphApplicationImpl(new File("."), getClass.getClassLoader, Mode.Test)

  def buildTheHiveModule(app: ScalligraphApplication): TheHiveModule = new TheHiveTestModule(app)

  def buildCortexConnector(app: ScalligraphApplication): CortexTestConnector = new CortexTestConnector(app)

  def buildDatabase(db: Database): Try[Unit] = new DatabaseBuilderModule(buildApp(db)).databaseBuilder.build(db)

  def buildApp(db: Database): TestApplication with WithTheHiveModule with WithCortexModule =
    new TestApplication(db, testApplicationNoDatabase) with WithTheHiveModule with WithCortexModule {
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

      override val thehiveModule: TheHiveModule = buildTheHiveModule(this)
      injectModule(thehiveModule)
      override val cortexConnector: CortexTestConnector = buildCortexConnector(this)
      injectModule(cortexConnector)
    }

  def testApp[A](body: TestApplication with WithTheHiveModule with WithCortexModule => A): A =
    JanusDatabaseProvider
      .withDatabase(databaseName, buildDatabase, testApplicationNoDatabase.actorSystem) { db =>
        body(buildApp(db))
      }
      .get
}

trait WithCortexModule {
  val cortexConnector: CortexTestConnector
}

class CortexTestConnector(app: ScalligraphApplication) extends CortexConnector(app) {
  import com.softwaremill.macwire._
  import com.softwaremill.macwire.akkasupport._
  import com.softwaremill.tagging._

  val thehiveModule: TheHiveModule = app.getModule[TheHiveModule]

  lazy val cortexClient: CortexClient                                                     = wire[TestCortexClientProvider].get
  override lazy val clientsConfig: ConfigItem[Seq[CortexClientConfig], Seq[CortexClient]] = TestConfigItem(Seq(cortexClient))
  override lazy val cortexActor: ActorRef @@ CortexTag                                    = wireAnonymousActor[CortexActor].taggedWith[CortexTag]
}
