package org.thp.thehive.connector.misp.controllers.v0

import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.NotFoundError

@Singleton
class Router @Inject() (mispCtrl: MispCtrl) extends SimpleRouter {

  override val routes: Routes = {
    case GET(p"/_syncAlerts")  => mispCtrl.sync
    case GET(p"/_cleanAlerts") => mispCtrl.cleanMispAlerts
//    case GET(p"/_syncAllAlerts")            => syncAllAlerts
//    case GET(p"/_syncArtifacts")            => syncArtifacts
    case POST(p"/export/$caseId/$mispName") => mispCtrl.exportCase(mispName, caseId)
    case r                                  => throw NotFoundError(s"${r.uri} not found")
  }
}
