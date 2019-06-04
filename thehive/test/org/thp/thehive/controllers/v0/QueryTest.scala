package org.thp.thehive.controllers.v0

import org.specs2.mock.Mockito
import org.thp.scalligraph.controllers.{FObject, FString, Field}
import org.thp.scalligraph.models.Database
import org.thp.thehive.services._
import play.api.libs.json.Json
import play.api.test.PlaySpecification

class QueryTest extends PlaySpecification with Mockito with QueryCtrl {

  val queryExecutor = new TheHiveQueryExecutor(
    mock[CaseSrv],
    mock[TaskSrv],
    mock[ObservableSrv],
    mock[AlertSrv],
    mock[LogSrv],
    mock[OrganisationSrv],
    mock[UserSrv],
    mock[Database]
  )

  "Controller" should {
    "parse stats query" in {
      val input = Json.parse("""
                               | {
                               |   "query": {
                               |     "_and": [{
                               |       "_in": {
                               |         "_field": "status",
                               |         "_values": ["waiting", "inProgress"]
                               |       }
                               |     }, {
                               |       "owner": "admin"
                               |     }]
                               |   },
                               |   "stats": [{
                               |     "_agg": "field",
                               |     "_field": "status",
                               |     "_select": [{ "_agg": "count"}]
                               |   }, {
                               |     "_agg": "count"
                               |   }]
                               | }
        """.stripMargin)

      val queryOrError = statsParser(FObject("_name" → FString("listTask")))(Field(input)).map(x ⇒ x)
      queryOrError.isGood must beTrue
      queryOrError.get must not be empty
    }
  }
}
