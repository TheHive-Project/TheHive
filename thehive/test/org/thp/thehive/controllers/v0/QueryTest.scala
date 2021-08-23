package org.thp.thehive.controllers.v0

import org.specs2.mock.Mockito
import org.thp.scalligraph.controllers.{Entrypoint, Field}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PublicProperties, QueryExecutor}
import org.thp.thehive.services._
import play.api.libs.json.Json
import play.api.test.PlaySpecification

class QueryTest extends PlaySpecification with Mockito {

  val publicTask = new PublicTask(mock[TaskSrv], mock[UserSrv], mock[OrganisationSrv], mock[CustomFieldSrv], mock[CustomFieldValueSrv])

  val queryExecutor: QueryExecutor = new QueryExecutor {
    override val limitedCountThreshold: Long = 1000
    override val db: Database                = mock[Database]
    override val version: (Int, Int)         = 0 -> 0
    override lazy val queries: Seq[ParamQuery[_]] =
      publicTask.initialQuery +: publicTask.getQuery +: publicTask.outputQuery +: publicTask.outputQuery +: publicTask.extraQueries
    override lazy val publicProperties: PublicProperties = publicTask.publicProperties
  }

  val taskCtrl = new TaskCtrl(
    mock[Entrypoint],
    mock[Database],
    mock[TaskSrv],
    mock[CaseSrv],
    mock[OrganisationSrv],
    mock[CustomFieldSrv],
    mock[CustomFieldValueSrv],
    queryExecutor,
    publicTask
  )

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
                               |       "owner": "admin@thehive.local"
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

      val queryOrError = taskCtrl.statsParser(Field(input))
      queryOrError.isGood must beTrue.updateMessage(s => s"$s\n$queryOrError")
      queryOrError.get    must not be empty
    }
  }
}
