package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.ApiMethod
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{AuthGraph, InitQuery, Query}
import org.thp.thehive.models.{Permissions, TheHiveSchema}
import play.api.mvc.{Action, AnyContent, Results}
@Singleton
class QueryCtrl @Inject()(apiMethod: ApiMethod, db: Database, theHiveSchema: TheHiveSchema) {
  val querySet = new QuerySet(theHiveSchema)

  val execute: Action[AnyContent] = apiMethod("query")
    .extract('query, Query.fieldsParser(querySet).on("query"))
    .requires(Permissions.read) { implicit request ⇒
      db.transaction { implicit graph ⇒
        val authGraph           = AuthGraph(Some(request), graph)
        val query: InitQuery[_] = request.body('query)
        Results.Ok(querySet.execute(query)(authGraph))
      }
    }
}
