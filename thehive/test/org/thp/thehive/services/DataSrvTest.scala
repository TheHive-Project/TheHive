package org.thp.thehive.services

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.test.PlaySpecification

import scala.util.Try

class DataSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv(userId = "user1@thehive.local", organisation = "cert")

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val dataSrv: DataSrv                  = app.instanceOf[DataSrv]
    val caseSrv                           = app.instanceOf[CaseSrv]
    val observableSrv                     = app.instanceOf[ObservableSrv]
    val observableTypeSrv                 = app.instanceOf[ObservableTypeSrv]
    val db: Database                      = app.instanceOf[Database]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

    s"[$name] data service" should {
      "create not existing data" in {
        val existing = db.roTransaction(implicit graph => dataSrv.initSteps.toList)
        existing.map(_.data) must contain("h.fr")

        val hFr  = existing.find(_.data == "h.fr").get
        val hFr2 = db.tryTransaction(implicit graph => dataSrv.create(Data("h.fr")))

        hFr2 must beSuccessfulTry.which(data => data._id shouldEqual hFr._id)

        db.tryTransaction(implicit graph => dataSrv.create(Data("h.fr2"))) must beSuccessfulTry.which(_._id must not(beEqualTo(hFr._id)))
      }

      "get related observables" in {
        db.tryTransaction(
          implicit graph => {
            observableSrv.create(
              Observable(Some("love"), 1, ioc = false, sighted = true),
              observableTypeSrv.get("domain").getOrFail().get,
              "love.com",
              Set("tagX"),
              Nil
            )
          }
        )

        db.roTransaction(implicit graph => dataSrv.initSteps.getByData("love.com").observables.exists() must beTrue)
      }

      "fetch not shared data" in {
        val c2 = db.roTransaction(implicit graph => caseSrv.get("#2").getOrFail().get)
        val observable = db
          .tryTransaction(
            implicit graph => {
              observableSrv.create(
                Observable(Some("hate"), 1, ioc = false, sighted = true),
                observableTypeSrv.get("domain").getOrFail().get,
                "h.fr",
                Set[String](),
                Nil
              )
            }
          )
          .get

        // Add observable to case and check for shared data state
        db.tryTransaction(implicit graph => {
          val r = caseSrv.addObservable(c2, observable)
          dataSrv.initSteps.getByData("h.fr").notShared(c2._id).exists() must beFalse
          r
        })

        ok
      }
    }
  }
}
