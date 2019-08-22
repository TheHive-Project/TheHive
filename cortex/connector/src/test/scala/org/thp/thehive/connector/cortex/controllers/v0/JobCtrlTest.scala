package org.thp.thehive.connector.cortex.controllers.v0

import akka.stream.Materializer
import gremlin.scala.{Key, P}
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv}
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.connector.cortex.services.CortexActor
import org.thp.thehive.models.{DatabaseBuilder, Permissions}
import org.thp.thehive.services.ObservableSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}

import scala.util.Try

class JobCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders().list) { dbProvider =>
    val app: AppBuilder = TestAppBuilder(dbProvider)
      .bindActor[CortexActor]("cortex-actor")

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val db                  = app.instanceOf[Database]
    val observableSrv       = app.instanceOf[ObservableSrv]
    val cortexQueryExecutor = app.instanceOf[CortexQueryExecutor]

    s"[$name] job controller" should {
      "get a job" in {
        val maybeObservable = db.roTransaction { implicit graph =>
          observableSrv.initSteps.has(Key("message"), P.eq("Some weird domain")).getOrFail()
        }

        maybeObservable must beSuccessfulTry

        val observable = maybeObservable.get
        val requestSearch = FakeRequest("POST", s"/api/connector/cortex/job/_search?range=0-200&sort=-startDate")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withJsonBody(Json.parse(s"""
              {
                 "query":{
                    "_and":[
                       {
                          "_parent":{
                             "_type":"case_artifact",
                             "_query":{
                                "_id":"${observable._id}"
                             }
                          }
                       }
                    ]
                 }
              }
            """.stripMargin))
        val resultSearch = cortexQueryExecutor.job.search(requestSearch)

        status(resultSearch) shouldEqual 200
      }

      "get stats for a job" in {
        val request = FakeRequest("POST", s"/api/connector/cortex/job/_stats")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withJsonBody(Json.parse(s"""
                                   {
                                     "query": {
                                       "_and": [{
                                         "_in": {
                                           "_field": "status",
                                           "_values": ["Waiting", "InProgress"]
                                         }
                                       }, {
                                         "analyzerId": "anaTest1"
                                       }]
                                     },
                                     "stats": [{
                                       "_agg": "field",
                                       "_field": "status",
                                       "_select": [{ "_agg": "count"}]
                                     }, {
                                       "_agg": "count"
                                     }]
                                   }
            """.stripMargin))
        val result = cortexQueryExecutor.job.stats(request)

        status(result) shouldEqual 200
      }
    }
  }
}
