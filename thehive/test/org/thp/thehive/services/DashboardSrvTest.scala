package org.thp.thehive.services

import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.models._
import play.api.libs.json.Json
import play.api.test.PlaySpecification

import scala.util.{Success, Try}

class DashboardSrvTest extends PlaySpecification {
  val dummyUserSrv = DummyUserSrv(userId = "user1@thehive.local", organisation = "cert")

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val dashboardSrv: DashboardSrv        = app.instanceOf[DashboardSrv]
    val orgaSrv                           = app.instanceOf[OrganisationSrv]
    val db: Database                      = app.instanceOf[Database]
    implicit val authContext: AuthContext = dummyUserSrv.getSystemAuthContext

    def createDashboard(title: String, shared: Boolean, definition: String) = {
      val r = db.tryTransaction(
        implicit graph =>
          dashboardSrv.create(
            Dashboard(title, s"desc $title", shared, definition),
            orgaSrv.getOrFail("cert").get
          )
      )

      r must beSuccessfulTry

      r.get
    }

    s"[$name] dashboard service" should {
      "create dashboards" in {
        val definition = """{
             "period":"custom",
             "items":[
                {
                   "type":"container",
                   "items":[
                      {
                         "type":"line",
                         "options":{
                            "title":"cases",
                            "entity":"case",
                            "field":"createdAt",
                            "interval":"1d",
                            "query":{

                            },
                            "series":[
                               {
                                  "agg":"count",
                                  "field":null,
                                  "type":"line",
                                  "label":"cases"
                               }
                            ]
                         },
                         "id":"37741393-eecc-16c9-f5b8-f0e668b403eb"
                      }
                   ]
                }
             ],
             "customPeriod":{
                "fromDate":"2019-07-08T22:00:00.000Z",
                "toDate":"2019-11-27T23:00:00.000Z"
             }
          }"""
        val d = createDashboard(
          "dashboard test 1",
          shared = false,
          definition
        )

        d.title shouldEqual "dashboard test 1"
        d.shared shouldEqual false
        d.definition shouldEqual definition
      }

      "update a dashboard" in {
        val d = createDashboard(
          "dashboard test 2",
          shared = false,
          "definition"
        )
        val updates = Seq(
          PropertyUpdater(FPathElem("definition"), "updated") { (vertex, _, _, _) =>
            vertex.property("definition", "updated")
            Success(Json.obj("definition" -> "updated"))
          },
          PropertyUpdater(FPathElem("shared"), false) { (vertex, _, _, _) =>
            vertex.property("shared", false)
            Success(Json.obj("shared" -> false))
          }
        )

        db.tryTransaction(implicit graph => dashboardSrv.update(dashboardSrv.get(d), updates)) must beSuccessfulTry
        val updatedDash = db.roTransaction(implicit graph => dashboardSrv.get(d).getOrFail().get)

        updatedDash.shared must beFalse
        updatedDash.definition shouldEqual "updated"
      }

      "update dashboard share status" in {
        val d = createDashboard(
          "dashboard test 3",
          shared = false,
          "definition"
        )

        db.tryTransaction(implicit graph => dashboardSrv.shareUpdate(d, status = true)) must beSuccessfulTry
        val updatedDash = db.roTransaction(implicit graph => dashboardSrv.get(d).getOrFail().get)

        updatedDash.shared must beTrue
      }

      "remove a dashboard" in {
        val d = createDashboard(
          "dashboard test 4",
          shared = true,
          "definition"
        )

        db.roTransaction(implicit graph => dashboardSrv.get(d).exists()) must beTrue
        db.tryTransaction(implicit graph => dashboardSrv.remove(d)) must beSuccessfulTry
        db.roTransaction(implicit graph => dashboardSrv.get(d).exists()) must beFalse
      }

      "show only visible dashboards" in {
        val d = createDashboard(
          "dashboard test 5",
          shared = true,
          "definition"
        )

        db.roTransaction(implicit graph => dashboardSrv.get(d).visible.exists()) must beTrue
        db.roTransaction(
          implicit graph => dashboardSrv.get(d).visible(DummyUserSrv(userId = "user2@thehive.local").authContext).exists()
        ) must beFalse
      }
    }
  }
}
