package org.thp.thehive.controllers.v1

import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

import javax.inject.{Inject, Singleton}

@Singleton
class Router @Inject()(
    caseCtrl: CaseCtrl,
    userCtrl: UserCtrl,
    organisationCtrl: OrganisationCtrl,
    shareCtrl: ShareCtrl,
    taskCtrl: TaskCtrl,
    customFieldCtrl: CustomFieldCtrl)
    extends SimpleRouter {

  override def routes: Routes = {
    case GET(p"/case")          ⇒ caseCtrl.list
    case POST(p"/case")         ⇒ caseCtrl.create
    case GET(p"/case/$caseId")  ⇒ caseCtrl.get(caseId)
    case PATCH(p"/case$caseId") ⇒ caseCtrl.update(caseId)

    case GET(p"/user")           ⇒ userCtrl.list
    case POST(p"/user")          ⇒ userCtrl.create
    case GET(p"/user/$userId")   ⇒ userCtrl.get(userId)
    case PATCH(p"/user/$userId") ⇒ userCtrl.update(userId)

    case GET(p"/organisation")                   ⇒ organisationCtrl.list
    case POST(p"/organisation")                  ⇒ organisationCtrl.create
    case GET(p"/organisation/$organisationId")   ⇒ organisationCtrl.get(organisationId)
    case PATCH(p"/organisation/$organisationId") ⇒ organisationCtrl.update(organisationId)

    case GET(p"/share")            ⇒ shareCtrl.list
    case POST(p"/share")           ⇒ shareCtrl.create
    case GET(p"/share/$shareId")   ⇒ shareCtrl.get(shareId)
    case PATCH(p"/share/$shareId") ⇒ shareCtrl.update(shareId)

    case GET(p"/task")           ⇒ taskCtrl.list
    case POST(p"/task")          ⇒ taskCtrl.create
    case GET(p"/task/$taskId")   ⇒ taskCtrl.get(taskId)
    case PATCH(p"/task/$taskId") ⇒ taskCtrl.update(taskId)

    case GET(p"/customField")  ⇒ customFieldCtrl.list
    case POST(p"/customField") ⇒ customFieldCtrl.create
//case POST(p"/query") => QueryCtrl.execute

  }
}
