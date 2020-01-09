package org.thp.thehive.connector.cortex.controllers.v0

import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.TestAppBuilder
import org.thp.thehive.services.ObservableSrv

class JobCtrlTest extends PlaySpecification with TestAppBuilder {
  "job controller" should {
    "get a job" in testApp { app =>
      val observable = app[Database].roTransaction { implicit graph =>
        app[ObservableSrv].initSteps.has("message", "Some weird domain").getOrFail().get
      }

      val requestSearch = FakeRequest("POST", s"/api/connector/cortex/job/_search?range=0-200&sort=-startDate")
        .withHeaders("user" -> "user2@thehive.local")
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
      val resultSearch = app[CortexQueryExecutor].job.search(requestSearch)
      status(resultSearch) shouldEqual 200
    }

    "get stats for a job" in testApp { app =>
      val request = FakeRequest("POST", s"/api/connector/cortex/job/_stats")
        .withHeaders("user" -> "user2@thehive.local")
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
      val result = app[CortexQueryExecutor].job.stats(request)

      status(result) shouldEqual 200
    }
  }
}
