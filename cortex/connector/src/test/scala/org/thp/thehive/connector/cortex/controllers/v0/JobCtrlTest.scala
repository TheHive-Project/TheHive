package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.cortex.client.{CortexClient, TestCortexClientProvider}
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.models.{Database, Schema}
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.{BasicDatabaseProvider, TestAppBuilder}
import org.thp.thehive.connector.cortex.models.TheHiveCortexSchemaProvider
import org.thp.thehive.connector.cortex.services.{Connector, CortexActor, TestConnector}
import org.thp.thehive.services.ObservableSrv
import play.api.libs.json.Json
import play.api.test.{FakeRequest, PlaySpecification}

class JobCtrlTest extends PlaySpecification with TestAppBuilder {
  override val databaseName: String = "thehiveCortex"
  override def appConfigure: AppBuilder =
    super
      .appConfigure
      .`override`(_.bindToProvider[Schema, TheHiveCortexSchemaProvider])
      .`override`(
        _.bindActor[CortexActor]("cortex-actor")
          .bindToProvider[CortexClient, TestCortexClientProvider]
          .bind[Connector, TestConnector]
          .bindToProvider[Schema, TheHiveCortexSchemaProvider]
          .bindNamedToProvider[Database, BasicDatabaseProvider]("with-thehive-cortex-schema")
      )

  "job controller" should {
    "get a job" in testApp { app =>
      val observable = app[Database].roTransaction { implicit graph =>
        app[ObservableSrv].initSteps.has("message", "Some weird domain").getOrFail("Observable").get
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
      val resultSearch = app[CortexQueryExecutor].job.search(requestSearch)
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
      val result = app[CortexQueryExecutor].job.stats(request)

      status(result) shouldEqual 200
    }
  }
}
