package org.thp.thehive.services

import org.thp.scalligraph.EntityName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.TraversalOps._

import org.thp.thehive.models._
import org.thp.thehive.services.DashboardOps._
import play.api.libs.json.{JsObject, Json}
import play.api.test.PlaySpecification

class DashboardSrvTest extends PlaySpecification with TestAppBuilder {
  implicit val authContext: AuthContext = DummyUserSrv(userId = "certuser@thehive.local", organisation = "cert").authContext

  s" dashboard service" should {
    "create dashboards" in testApp { app =>
      import app._
      import app.thehiveModule._

      val definition =
        Json
          .parse("""{
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
          }""")
          .as[JsObject]
      database.tryTransaction { implicit graph =>
        dashboardSrv.create(Dashboard("dashboard test 1", "desc dashboard test 1", definition))
      } must beASuccessfulTry.which { d =>
        d.title shouldEqual "dashboard test 1"
        d.organisationShares must beEmpty
        d.definition shouldEqual definition
      }
    }

    "share a dashboard" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          dashboard <- dashboardSrv.startTraversal.has(_.title, "dashboard soc").getOrFail("Dashboard")
          _ = dashboardSrv.get(dashboard).visible.headOption must beNone
          _ <- dashboardSrv.share(dashboard, EntityName("cert"), writable = false)
          _ = dashboardSrv.get(dashboard).visible.headOption must beSome
        } yield ()
      } must beASuccessfulTry
    }
//    "update dashboard share status" in testApp { app =>
//    import app._
//    import app.testModule._
//      database.tryTransaction { implicit graph =>
//        for {
//          dashboard <- dashboardSrv.initSteps.has("title", "dashboard-cert").getOrFail()
//          _         <- dashboardSrv.shareUpdate(dashboard, status = true)
//        } yield ()
//      } must beSuccessfulTry
//    }

    "remove a dashboard" in testApp { app =>
      import app._
      import app.thehiveModule._

      database.tryTransaction { implicit graph =>
        for {
          dashboard <- dashboardSrv.startTraversal.has(_.title, "dashboard soc").getOrFail("Dashboard")
          _         <- dashboardSrv.remove(dashboard)
        } yield dashboardSrv.startTraversal.has(_.title, "dashboard soc").exists
      } must beASuccessfulTry(false)
    }
  }
}
