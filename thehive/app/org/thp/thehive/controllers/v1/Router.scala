package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

@Singleton
class Router @Inject() (
    caseCtrl: CaseCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
    userCtrl: UserCtrl,
    organisationCtrl: OrganisationCtrl,
    taskCtrl: TaskCtrl,
    customFieldCtrl: CustomFieldCtrl,
    alertCtrl: AlertCtrl,
    taxonomyCtrl: TaxonomyCtrl,
    auditCtrl: AuditCtrl,
    statusCtrl: StatusCtrl,
    authenticationCtrl: AuthenticationCtrl,
    describeCtrl: DescribeCtrl
) extends SimpleRouter {

  override def routes: Routes = {
    case GET(p"/status") => statusCtrl.get
//    GET  /health                              controllers.StatusCtrl.health
//    GET      /logout                              controllers.AuthenticationCtrl.logout()
    case GET(p"/logout")                 => authenticationCtrl.logout
    case POST(p"/logout")                => authenticationCtrl.logout
    case POST(p"/login")                 => authenticationCtrl.login
    case POST(p"/auth/totp/set")         => authenticationCtrl.totpSetSecret
    case POST(p"/auth/totp/unset")       => authenticationCtrl.totpUnsetSecret(None)
    case POST(p"/auth/totp/unset/$user") => authenticationCtrl.totpUnsetSecret(Some(user))

    case POST(p"/case")                 => caseCtrl.create
    case GET(p"/case/$caseId")          => caseCtrl.get(caseId)
    case PATCH(p"/case/$caseId")        => caseCtrl.update(caseId)
    case POST(p"/case/_merge/$caseIds") => caseCtrl.merge(caseIds)
    case DELETE(p"/case/$caseId")       => caseCtrl.delete(caseId)
//    case PATCH(p"api/case/_bulk") =>                          caseCtrl.bulkUpdate()
//    case POST(p"/case/_stats") =>                        caseCtrl.stats()
//    case GET(p"/case/$caseId/links") =>                  caseCtrl.linkedCases(caseId)

    case GET(p"/caseTemplate")                   => caseTemplateCtrl.list
    case POST(p"/caseTemplate")                  => caseTemplateCtrl.create
    case GET(p"/caseTemplate/$caseTemplateId")   => caseTemplateCtrl.get(caseTemplateId)
    case PATCH(p"/caseTemplate/$caseTemplateId") => caseTemplateCtrl.update(caseTemplateId)
    //case DELETE(p"/caseTemplate/$caseTemplateId") => caseTemplateCtrl.delete(caseTemplateId)

    case POST(p"/user")                                                   => userCtrl.create
    case GET(p"/user/current")                                            => userCtrl.current
    case GET(p"/user/$userId")                                            => userCtrl.get(userId)
    case PATCH(p"/user/$userId")                                          => userCtrl.update(userId)
    case DELETE(p"/user/$userId")                                         => userCtrl.lock(userId)
    case DELETE(p"/user/$userId/force" ? q_o"organisation=$organisation") => userCtrl.delete(userId, organisation)
    case POST(p"/user/$userId/password/set")                              => userCtrl.setPassword(userId)
    case POST(p"/user/$userId/password/change")                           => userCtrl.changePassword(userId)
    case GET(p"/user/$userId/key")                                        => userCtrl.getKey(userId)
    case DELETE(p"/user/$userId/key")                                     => userCtrl.removeKey(userId)
    case POST(p"/user/$userId/key/renew")                                 => userCtrl.renewKey(userId)
    case GET(p"/user/$userId/avatar$file*")                               => userCtrl.avatar(userId)

    case POST(p"/organisation")                  => organisationCtrl.create
    case GET(p"/organisation/$organisationId")   => organisationCtrl.get(organisationId)
    case PATCH(p"/organisation/$organisationId") => organisationCtrl.update(organisationId)

//    case GET(p"/share")            => shareCtrl.list
//    case POST(p"/share")           => shareCtrl.create
//    case GET(p"/share/$shareId")   => shareCtrl.get(shareId)
//    case PATCH(p"/share/$shareId") => shareCtrl.update(shareId)

    case GET(p"/task")           => taskCtrl.list
    case POST(p"/task")          => taskCtrl.create
    case GET(p"/task/$taskId")   => taskCtrl.get(taskId)
    case PATCH(p"/task/$taskId") => taskCtrl.update(taskId)
    // POST     /case/:caseId/task/_search           controllers.TaskCtrl.findInCase(caseId)
    // POST     /case/task/_stats                    controllers.TaskCtrl.stats()

    case GET(p"/customField")  => customFieldCtrl.list
    case POST(p"/customField") => customFieldCtrl.create

    case POST(p"/alert")                   => alertCtrl.create
    case GET(p"/alert/$alertId")           => alertCtrl.get(alertId)
    case PATCH(p"/alert/$alertId")         => alertCtrl.update(alertId)
    case POST(p"/alert/$alertId/read")     => alertCtrl.markAsRead(alertId)
    case POST(p"/alert/$alertId/unread")   => alertCtrl.markAsUnread(alertId)
    case POST(p"/alert/$alertId/follow")   => alertCtrl.followAlert(alertId)
    case POST(p"/alert/$alertId/unfollow") => alertCtrl.unfollowAlert(alertId)
    case POST(p"/alert/$alertId/case")     => alertCtrl.createCase(alertId)
    // PATCH    /alert/_bulk                         controllers.AlertCtrl.bulkUpdate()
//    DELETE   /alert/:alertId                      controllers.AlertCtrl.delete(alertId)
//    POST     /alert/:alertId/merge/:caseId        controllers.AlertCtrl.mergeWithCase(alertId, caseId)

    case POST(p"/taxonomy")                   => taxonomyCtrl.create
    case POST(p"/taxonomy/import-zip")        => taxonomyCtrl.importZip
    case GET(p"/taxonomy/$taxoId")            => taxonomyCtrl.get(taxoId)
    case PUT(p"/taxonomy/$taxoId/activate")   => taxonomyCtrl.toggleActivation(taxoId, isActive = true)
    case PUT(p"/taxonomy/$taxoId/deactivate") => taxonomyCtrl.toggleActivation(taxoId, isActive = false)
    case DELETE(p"/taxonomy/$taxoId")         => taxonomyCtrl.delete(taxoId)

    case GET(p"/audit") => auditCtrl.flow
//      GET      /flow                                controllers.AuditCtrl.flow(rootId: Option[String], count: Option[Int])
//    GET      /audit                               controllers.AuditCtrl.find()
//    POST     /audit/_search                       controllers.AuditCtrl.find()
//    POST     /audit/_stats                        controllers.AuditCtrl.stats()

    case GET(p"/describe/_all")       => describeCtrl.describeAll
    case GET(p"/describe/$modelName") => describeCtrl.describe(modelName)

  }
}
