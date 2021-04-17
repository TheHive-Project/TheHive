package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.services.ObservableSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class JobCtrlTest extends PlaySpecification with TestAppBuilder {
  override val databaseName: String = "thehiveCortex"
//  override def appConfigure: AppBuilder =
//    super
//      .appConfigure
//      .`override`(
//        _.bindActor[CortexActor]("cortex-actor")
//          .bindToProvider[CortexClient, TestCortexClientProvider]
//          .bind[Connector, TestConnector]
////          .bindToProvider[Schema, TheHiveCortexSchemaProvider]
//          .bindNamedToProvider[QueryExecutor, TheHiveCortexQueryExecutorProvider]("v0")
//      )

  "job controller" should {
    "get a job" in testApp { app =>
      val observable = app[Database].roTransaction { implicit graph =>
        app[ObservableSrv].startTraversal.has(_.message, "Some weird domain").getOrFail("Observable").get
      }

      val requestSearch = FakeRequest("POST", s"/api/connector/cortex/job/_search?range=0-200&sort=-startDate")
        .withHeaders("user" -> "certuser@thehive.local")
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
      val resultSearch = app[JobCtrl].search(requestSearch)
      status(resultSearch) shouldEqual 200
    }

    "get stats for a job" in testApp { app =>
      val request = FakeRequest("POST", s"/api/connector/cortex/job/_stats")
        .withHeaders("user" -> "certuser@thehive.local")
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
      val result = app[JobCtrl].stats(request)

      status(result) shouldEqual 200
    }
  }
}
