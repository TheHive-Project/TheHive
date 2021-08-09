package org.thp.thehive.services

import akka.actor.ActorRef
import com.softwaremill.macwire.akkasupport.wireAnonymousActor
import org.thp.scalligraph.ScalligraphApplication
import org.thp.scalligraph.models.Database
import org.thp.thehive._
import org.thp.thehive.services.notification.NotificationTag

import scala.util.Try

trait TestAppBuilder extends LogFileConfig {
  val databaseName: String = "default"

  def buildTheHiveModule(app: ScalligraphApplication): TheHiveModule = new TheHiveTestModule(app)
  def destroyTheHiveModule(thehiveModule: TheHiveModule): Unit       = ()

  def buildApp(db: Database): TestApplication with WithTheHiveModule =
    new TestApplication(db) with WithTheHiveModule {
      override val thehiveModule: TheHiveModule = buildTheHiveModule(this)
      injectModule(thehiveModule)
    }

  def destroyApp(app: TestApplication with WithTheHiveModule): Unit = {
    destroyTheHiveModule(app.thehiveModule)
    app.terminate()
  }

  def buildDatabase(db: Database): Try[Unit] = new DatabaseBuilderModule(buildApp(db)).databaseBuilder.build(db)

  def testApp[A](body: TestApplication with WithTheHiveModule => A): A =
    JanusDatabaseProvider
      .withDatabase(databaseName, buildDatabase, TestApplication.actorSystemForDB) { db =>
        val app = buildApp(db)
        try body(app)
        finally destroyApp(app)
      }
      .get
}

trait WithTheHiveModule {
  val thehiveModule: TheHiveModule
}

class TheHiveTestModule(app: ScalligraphApplication) extends TheHiveModule(app) {
  import com.softwaremill.tagging._

  lazy val dummyActor: ActorRef                                        = wireAnonymousActor[DummyActor]
  override lazy val notificationActor: ActorRef @@ NotificationTag     = dummyActor.taggedWith[NotificationTag]
  override lazy val flowActor: ActorRef @@ FlowTag                     = dummyActor.taggedWith[FlowTag]
  override lazy val integrityCheckActor: ActorRef @@ IntegrityCheckTag = dummyActor.taggedWith[IntegrityCheckTag]
}
