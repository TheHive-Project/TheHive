package org.thp.thehive.connector.cortex.services

import akka.stream.Materializer
import gremlin.scala.{Key, P}
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthContext, AuthSrv, Permission, UserSrv}
import org.thp.scalligraph.controllers.TestAuthSrv
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.{AlertSrv, LocalUserSrv, ObservableSrv, TaskSrv}
import play.api.test.{NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.util.Try

class EntityHelperTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthSrv, TestAuthSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.initialAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val entityHelper = app.instanceOf[EntityHelper]
    val db           = app.instanceOf[Database]
    implicit val authContext: AuthContext = new AuthContext {
      override def userId: String               = "user1"
      override def userName: String             = "user1"
      override def organisation: String         = "cert"
      override def requestId: String            = ""
      override def permissions: Set[Permission] = Permissions.all
    }

    s"[$name] entity helper" should {

      "return appropriate entity threat levels" in db.roTransaction { implicit graph =>
        val taskSrv: TaskSrv = app.instanceOf[TaskSrv]
        val t1               = taskSrv.initSteps.toList.find(_.title == "case 1 task 1")
        t1 must beSome
        val task1 = t1.get

        val successTask = entityHelper.entityInfo(task1)
        val failureTask = entityHelper.entityInfo(task1)(graph, dummyUserSrv.authContext)

        successTask must beSuccessfulTry
        failureTask must beFailedTry

        val (_, tlpTask, papTask) = successTask.get

        tlpTask shouldEqual 2
        papTask shouldEqual 2
      }

      "return proper observable threat levels" in db.roTransaction { implicit graph =>
        val observableSrv: ObservableSrv = app.instanceOf[ObservableSrv]
        val o1                           = observableSrv.initSteps.has(Key("tlp"), P.eq(3)).headOption()
        o1 must beSome
        val observable1 = o1.get

        val successObs = entityHelper.entityInfo(observable1)
        successObs must beSuccessfulTry

        val (_, tlp, pap) = successObs.get
        tlp shouldEqual 3
        pap shouldEqual 2
      }

      "find a manageable entity only (task)" in db.roTransaction { implicit graph =>
        val taskSrv: TaskSrv = app.instanceOf[TaskSrv]
        val t2               = taskSrv.initSteps.toList.find(_.title == "case 1 task 2")
        t2 must beSome
        val task2 = t2.get

        val successTask = entityHelper.get("Task", task2._id, Permissions.manageAction)
        val failureTask = entityHelper.get("Task", task2._id, Permissions.manageAction)(graph, dummyUserSrv.authContext)

        successTask must beSuccessfulTry
        failureTask must beFailedTry
      }

      "find a manageable entity only (alert)" in db.roTransaction { implicit graph =>
        val alertSrv: AlertSrv = app.instanceOf[AlertSrv]
        val a1                 = alertSrv.get("testType;testSource;ref2").headOption()
        a1 must beSome
        val alert1 = a1.get

        val successAlert = entityHelper.get("Alert", alert1._id, Permissions.manageAction)
        successAlert must beSuccessfulTry
      }

    }
  }
}
