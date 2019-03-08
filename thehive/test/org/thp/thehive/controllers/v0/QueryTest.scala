package org.thp.thehive.controllers.v0
import javax.inject.Singleton
import org.scalactic.Good
import org.specs2.mock.Mockito
import org.thp.scalligraph.controllers.{FObject, FString, Field}
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.{CaseSrv, TaskSrv}
import play.api.libs.json.Json
import play.api.test.PlaySpecification

class QueryTest extends PlaySpecification with Mockito with QueryCtrl {

  @Singleton
  val queryExecutor = new TheHiveQueryExecutor(
    mock[CaseSrv],
    mock[TaskSrv],
    mock[Database]
  )
//  val queryCtrl = new QueryCtrl(thehiveQueryExecutor)

  "Controller" should {
    "parse stats query" in {
      val input = Json.parse("""
{
  "query": {
    "_and": [{
      "_in": {
        "_field": "status",
        "_values": ["Waiting", "InProgress"]
      }
    }, {
      "owner": "admin"
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
        """.stripMargin)

      val queryOrError = statsParser(FObject("_name" → FString("listTask")))(Field(input)).map(x ⇒ x)
      queryOrError must_=== Good(Nil)
      /*
      Bad(Many(
      Invalid format for :
        FObject(Map(_in -> FObject(Map(_field -> FString(status), _values -> FSeq(List(FString(Waiting), FString(InProgress))))))),
        expected query (query,
       Invalid format for :
        FObject(Map(owner -> FString(admin))),
        expected query (query,
       Invalid format for :
        FObject(Map(_in -> FObject(Map(_field -> FString(status), _values -> FSeq(List(FString(Waiting), FString(InProgress))))))),
        expected query (query,
       Invalid format for :
        FObject(Map(owner -> FString(admin))),
        expected query (query))
     */
    }
  }
}
