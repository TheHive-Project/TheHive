package org.thp.thehive.controllers.v0

import play.api.libs.json.Json
import play.api.test.PlaySpecification

import org.specs2.mock.Mockito
import org.thp.scalligraph.controllers.{EntryPoint, FObject, FString, Field}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, QueryExecutor}
import org.thp.thehive.services.{CaseSrv, OrganisationSrv, TaskSrv, UserSrv}

class QueryTest extends PlaySpecification with Mockito {

  val taskCtrl = new TaskCtrl(
    mock[EntryPoint],
    mock[Database],
    mock[TaskSrv],
    mock[CaseSrv],
    mock[UserSrv],
    mock[OrganisationSrv]
  )

  val queryExecutor: QueryExecutor = new QueryExecutor {
    override val version: (Int, Int)                               = 0 -> 0
    override lazy val queries: Seq[ParamQuery[_]]                  = Seq(taskCtrl.initialQuery, taskCtrl.pageQuery, taskCtrl.outputQuery)
    override lazy val publicProperties: List[PublicProperty[_, _]] = taskCtrl.publicProperties
  }
  val queryCtrl: QueryCtrl = new QueryCtrlBuilder(mock[EntryPoint], mock[Database]).apply(taskCtrl, queryExecutor)

  "Controller" should {
    "parse stats query" in {
      val input = Json.parse("""
                               | {
                               |   "query": {
                               |     "_and": [{
                               |       "_in": {
                               |         "_field": "status",
                               |         "_values": ["Waiting", "InProgress"]
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

      val queryOrError = queryCtrl.statsParser(FObject("_name" -> FString("listTask")))(Field(input))
      queryOrError.isGood must beTrue.updateMessage(s => s"$s\n$queryOrError")
      queryOrError.get must not be empty
    }
  }
}
