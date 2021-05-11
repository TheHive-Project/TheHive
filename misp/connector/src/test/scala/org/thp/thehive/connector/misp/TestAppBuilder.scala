package org.thp.thehive.connector.misp

import org.thp.scalligraph.ScalligraphApplication
import org.thp.scalligraph.models.Database
import org.thp.thehive._
import org.thp.thehive.connector.misp.services.{TestMispClientProvider, TheHiveMispClient}
import org.thp.thehive.services.{TheHiveTestModule, WithTheHiveModule}

import scala.util.Try

trait TestAppBuilder extends LogFileConfig {
  val databaseName: String = "default"

  def buildTheHiveModule(app: ScalligraphApplication): TheHiveModule = new TheHiveTestModule(app)
  def destroyTheHiveModule(thehiveModule: TheHiveModule): Unit       = ()

  def buildMispConnector(app: ScalligraphApplication): MispTestModule = new MispTestModule(app)
  def destroyMispModule(mispModule: MispTestModule): Unit             = ()

  def buildDatabase(db: Database): Try[Unit] = new DatabaseBuilderModule(buildApp(db)).databaseBuilder.build(db)

  def buildApp(db: Database): TestApplication with WithTheHiveModule with WithMispModule =
    new TestApplication(db) with WithTheHiveModule with WithMispModule {
      override val thehiveModule: TheHiveModule = buildTheHiveModule(this)
      injectModule(thehiveModule)
      override val mispModule: MispTestModule = buildMispConnector(this)
      injectModule(mispModule)
    }

  def destroyApp(app: TestApplication with WithTheHiveModule with WithMispModule): Unit = {
    destroyTheHiveModule(app.thehiveModule)
    destroyMispModule(app.mispModule)
  }

  def testApp[A](body: TestApplication with WithTheHiveModule with WithMispModule => A): A =
    JanusDatabaseProvider
      .withDatabase(databaseName, buildDatabase, TestApplication.appWithoutDatabase.actorSystem) { db =>
        val app = buildApp(db)
        val res = body(app)
        destroyApp(app)
        res
      }
      .get
}

trait WithMispModule {
  val mispModule: MispTestModule
}

class MispTestModule(app: ScalligraphApplication) extends MispModule(app) {

  import com.softwaremill.macwire._

  lazy val theHiveMispClient: TheHiveMispClient = wire[TestMispClientProvider].get()
}
