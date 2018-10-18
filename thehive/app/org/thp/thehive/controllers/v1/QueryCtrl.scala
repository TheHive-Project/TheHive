package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.ApiMethod
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{AuthGraph, Query}
import org.thp.thehive.models.Permissions
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class QueryCtrl @Inject()(apiMethod: ApiMethod, db: Database, queryExecutor: TheHiveQueryExecutor) {

  val execute: Action[AnyContent] = apiMethod("query")
    .extract('query, queryExecutor.parser.on("query"))
    .requires(Permissions.read) { implicit request ⇒
      db.transaction { implicit graph ⇒
        val authGraph    = AuthGraph(Some(request), graph)
        val query: Query = request.body('query)
        Results.Ok(queryExecutor.execute(query)(authGraph).toJson)
      }
    }
}
