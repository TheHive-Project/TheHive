package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.Query
import org.thp.thehive.controllers.v0.QueryCtrl
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class JobCtrl @Inject()(entryPoint: EntryPoint, db: Database, val queryExecutor: CortexQueryExecutor) extends QueryCtrl {

  def search: Action[AnyContent] =
    entryPoint("search job")
      .extract('query, searchParser("listJob"))
      .authTransaction(db) { implicit request ⇒ graph ⇒
        val query: Query = request.body('query)
        val result       = queryExecutor.execute(query, graph, request.authContext)
        val resp         = Results.Ok((result.toJson \ "result").as[JsValue])
        result.toOutput match {
          case PagedResult(_, Some(size)) ⇒ Success(resp.withHeaders("X-Total" → size.toString))
          case _                          ⇒ Success(resp)
        }
      }
}
