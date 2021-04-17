package org.thp.thehive.controllers

import controllers.ExternalAssets
import org.thp.scalligraph.ScalligraphModule
import org.thp.scalligraph.auth.AuthSrvProvider
import org.thp.scalligraph.models.UpdatableSchema
import org.thp.scalligraph.query.QueryExecutor
import play.api.Mode
import play.api.routing.sird._
import play.api.routing.{Router, SimpleRouter}

object TheHiveFrontModule extends ScalligraphModule {
  import scalligraphApplication._

  lazy val extAssets: ExternalAssets = new ExternalAssets(environment)(executionContext, fileMimeTypes)
  override lazy val routers: Set[Router] = Set(context.environment.mode match {
    case Mode.Prod => ???
    case _ =>
      SimpleRouter {
        case GET(p"/$file*") if !file.startsWith("api/") && file.startsWith("bower_components") => extAssets.at("frontend", file)
        case GET(p"/$file*") if !file.startsWith("api/")                                        => extAssets.at("frontend/app", file)
      }
  })

  override val queryExecutors: Set[QueryExecutor]     = Set.empty
  override val schemas: Set[UpdatableSchema]          = Set.empty
  override val authSrvProviders: Set[AuthSrvProvider] = Set.empty
}
