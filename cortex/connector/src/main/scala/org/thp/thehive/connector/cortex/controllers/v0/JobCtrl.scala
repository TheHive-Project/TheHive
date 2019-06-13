package org.thp.thehive.connector.cortex.controllers.v0

import scala.util.Success

import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.{Database, PagedResult}
import org.thp.scalligraph.query.Query
import org.thp.thehive.controllers.v0.QueryCtrl

@Singleton
class JobCtrl @Inject()(entryPoint: EntryPoint, db: Database, val queryExecutor: CortexQueryExecutor) extends QueryCtrl {

  def search: Action[AnyContent] =
    entryPoint("search job")
      .extract('query, searchParser("listAlert"))
      .authTransaction(db) { implicit request ⇒ graph ⇒
//        val query: Query = request.body('query)
//        val result       = queryExecutor.execute(query, graph, request.authContext)
//        val resp         = Results.Ok((result.toJson \ "result").as[JsValue])
//        result.toOutput match {
//          case PagedResult(_, Some(size)) ⇒ Success(resp.withHeaders("X-Total" → size.toString))
//          case _                          ⇒ Success(resp)
//        }
        Success(Results.Ok(JsArray.empty))
      }
}
