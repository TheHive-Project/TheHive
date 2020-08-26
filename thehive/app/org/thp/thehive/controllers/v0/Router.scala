package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

@Singleton
class Router @Inject() (
    statsCtrl: StatsCtrl,
    caseCtrl: CaseCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
    userCtrl: UserCtrl,
    organisationCtrl: OrganisationCtrl,
    taskCtrl: TaskCtrl,
    logCtrl: LogCtrl,
    observableCtrl: ObservableCtrl,
    customFieldCtrl: CustomFieldCtrl,
    alertCtrl: AlertCtrl,
    auditCtrl: AuditCtrl,
    statusCtrl: StatusCtrl,
    dashboardCtrl: DashboardCtrl,
    authenticationCtrl: AuthenticationCtrl,
    listCtrl: ListCtrl,
    streamCtrl: StreamCtrl,
    attachmentCtrl: AttachmentCtrl,
    describeCtrl: DescribeCtrl,
    configCtrl: ConfigCtrl,
    profileCtrl: ProfileCtrl,
    shareCtrl: ShareCtrl,
    tagCtrl: TagCtrl,
    queryExecutor: TheHiveQueryExecutor,
    pageCtrl: PageCtrl,
    permissionCtrl: PermissionCtrl,
    observableTypeCtrl: ObservableTypeCtrl
) extends SimpleRouter {

  override def routes: Routes = {

    case POST(p"/_stats") => statsCtrl.stats

    case GET(p"/status")  => statusCtrl.get
    case GET(p"/health")  => statusCtrl.health
    case GET(p"/logout")  => authenticationCtrl.logout
    case POST(p"/logout") => authenticationCtrl.logout
    case POST(p"/login")  => authenticationCtrl.login
//    case POST(p"/ssoLogin") => authenticationCtrl.ssoLogin

    case DELETE(p"/case/shares")                               => shareCtrl.removeShares()
    case POST(p"/case/$caseId/shares")                         => shareCtrl.shareCase(caseId)
    case DELETE(p"/case/$caseId/shares")                       => shareCtrl.removeShares(caseId)
    case DELETE(p"/task/$taskId/shares")                       => shareCtrl.removeTaskShares(taskId)
    case DELETE(p"/observable/$observableId/shares")           => shareCtrl.removeObservableShares(observableId)
    case GET(p"/case/$caseId/shares")                          => shareCtrl.listShareCases(caseId)
    case GET(p"/case/$caseId/task/$taskId/shares")             => shareCtrl.listShareTasks(caseId, taskId)
    case GET(p"/case/$caseId/observable/$observableId/shares") => shareCtrl.listShareObservables(caseId, observableId)
    case POST(p"/case/task/$taskId/shares")                    => shareCtrl.shareTask(taskId)
    case DELETE(p"/case/share/$shareId")                       => shareCtrl.removeShare(shareId)
    case PATCH(p"/case/share/$shareId")                        => shareCtrl.updateShare(shareId)

    case GET(p"/case/task")           => queryExecutor.task.search
    case POST(p"/case/$caseId/task")  => taskCtrl.create(caseId) // Audit ok
    case GET(p"/case/task/$taskId")   => taskCtrl.get(taskId)
    case PATCH(p"/case/task/$taskId") => taskCtrl.update(taskId) // Audit ok
    case POST(p"/case/task/_search")  => queryExecutor.task.search
    //case POST(p"/case/$caseId/task/_search") => taskCtrl.search
    case POST(p"/case/task/_stats") => queryExecutor.task.stats

    //case GET(p"/case/task/$taskId/log") => logCtrl.findInTask(taskId)
    //case POST(p"/case/task/$taskId/log/_search") => logCtrl.findInTask(taskId)
    case POST(p"/case/task/log/_search")  => queryExecutor.log.search
    case POST(p"/case/task/log/_stats")   => queryExecutor.log.stats
    case POST(p"/case/task/$taskId/log")  => logCtrl.create(taskId) // Audit ok
    case PATCH(p"/case/task/log/$logId")  => logCtrl.update(logId) // Audit ok
    case DELETE(p"/case/task/log/$logId") => logCtrl.delete(logId) // Audit ok, weird logs/silent errors though (stream related)
    //    case GET(p"/case/task/log/$logId") => logCtrl.get(logId)

    case POST(p"/case/artifact/_search") => queryExecutor.observable.search
    //    case POST(p"/case/:caseId/artifact/_search")    ⇒ observableCtrl.findInCase(caseId)
    case POST(p"/case/artifact/_stats")               => queryExecutor.observable.stats
    case POST(p"/case/$caseId/artifact")              => observableCtrl.create(caseId) // Audit ok
    case GET(p"/case/artifact/$observableId")         => observableCtrl.get(observableId)
    case DELETE(p"/case/artifact/$observableId")      => observableCtrl.delete(observableId) // Audit ok
    case PATCH(p"/case/artifact/_bulk")               => observableCtrl.bulkUpdate // Audit ok
    case PATCH(p"/case/artifact/$observableId")       => observableCtrl.update(observableId) // Audit ok
    case GET(p"/case/artifact/$observableId/similar") => observableCtrl.findSimilar(observableId)
    case POST(p"/case/artifact/$observableId/shares") => shareCtrl.shareObservable(observableId)

    case GET(p"/case")                  => queryExecutor.`case`.search
    case POST(p"/case")                 => caseCtrl.create // Audit ok
    case GET(p"/case/$caseId")          => caseCtrl.get(caseId)
    case PATCH(p"/case/_bulk")          => caseCtrl.bulkUpdate // Not used by the frontend
    case PATCH(p"/case/$caseId")        => caseCtrl.update(caseId) // Audit ok
    case POST(p"/case/_merge/$caseIds") => caseCtrl.merge(caseIds) // Not implemented in backend and not used by frontend
    case DELETE(p"/case/$caseId")       => caseCtrl.delete(caseId) // Not used by frontend
    case POST(p"/case/_search")         => queryExecutor.`case`.search
    case POST(p"/case/_stats")          => queryExecutor.`case`.stats
    case DELETE(p"/case/$caseId/force") => caseCtrl.realDelete(caseId) // Audit ok
    case GET(p"/case/$caseId/links")    => caseCtrl.linkedCases(caseId)

    case GET(p"/case/template")                    => queryExecutor.caseTemplate.search
    case POST(p"/case/template")                   => caseTemplateCtrl.create // Audit ok
    case GET(p"/case/template/$caseTemplateId")    => caseTemplateCtrl.get(caseTemplateId)
    case PATCH(p"/case/template/$caseTemplateId")  => caseTemplateCtrl.update(caseTemplateId) // Audit ok
    case POST(p"/case/template/_search")           => queryExecutor.caseTemplate.search
    case DELETE(p"/case/template/$caseTemplateId") => caseTemplateCtrl.delete(caseTemplateId) // Audit ok

    case GET(p"/user")                          => queryExecutor.user.search
    case POST(p"/user")                         => userCtrl.create // Audit ok
    case GET(p"/user/current")                  => userCtrl.current
    case GET(p"/user/$userId")                  => userCtrl.get(userId)
    case PATCH(p"/user/$userId")                => userCtrl.update(userId) // Audit ok
    case DELETE(p"/user/$userId")               => userCtrl.lock(userId) // Audit ok
    case DELETE(p"/user/$userId/force")         => userCtrl.delete(userId) // Audit ok
    case POST(p"/user/$userId/password/set")    => userCtrl.setPassword(userId) // Audit ok
    case POST(p"/user/$userId/password/change") => userCtrl.changePassword(userId) // Audit ok
    case GET(p"/user/$userId/key")              => userCtrl.getKey(userId)
    case DELETE(p"/user/$userId/key")           => userCtrl.removeKey(userId) // Audit ok
    case POST(p"/user/$userId/key/renew")       => userCtrl.renewKey(userId) // Audit ok
    case POST(p"/user/_search")                 => queryExecutor.user.search

    case GET(p"/list")                    => listCtrl.list
    case DELETE(p"/list/$itemId")         => listCtrl.deleteItem(itemId)
    case PATCH(p"/list/$itemId")          => listCtrl.updateItem(itemId)
    case POST(p"/list/$listName")         => listCtrl.addItem(listName)
    case GET(p"/list/$listName")          => listCtrl.listItems(listName)
    case POST(p"/list/$listName/_exists") => listCtrl.itemExists(listName)

    case GET(p"/organisation")                                           => organisationCtrl.list
    case POST(p"/organisation")                                          => organisationCtrl.create // Audit ok
    case GET(p"/organisation/$organisationId")                           => organisationCtrl.get(organisationId)
    case GET(p"/organisation/$organisationId/links")                     => organisationCtrl.listLinks(organisationId)
    case PATCH(p"/organisation/$organisationId")                         => organisationCtrl.update(organisationId) // Audit ok
    case PUT(p"/organisation/$organisationId1/link/$organisationId2")    => organisationCtrl.link(organisationId1, organisationId2)
    case PUT(p"/organisation/$organisationId1/links")                    => organisationCtrl.bulkLink(organisationId1)
    case DELETE(p"/organisation/$organisationId1/link/$organisationId2") => organisationCtrl.unlink(organisationId1, organisationId2)

    case GET(p"/customField")          => customFieldCtrl.list
    case POST(p"/customField")         => customFieldCtrl.create
    case GET(p"/customField/$id")      => customFieldCtrl.get(id)
    case DELETE(p"/customField/$id")   => customFieldCtrl.delete(id)
    case PATCH(p"/customField/$id")    => customFieldCtrl.update(id)
    case GET(p"/customFields/$id/use") => customFieldCtrl.useCount(id)

    case GET(p"/alert")                        => queryExecutor.alert.search
    case POST(p"/alert")                       => alertCtrl.create // Audit ok
    case GET(p"/alert/$alertId")               => alertCtrl.get(alertId)
    case PATCH(p"/alert/$alertId")             => alertCtrl.update(alertId) // Audit ok
    case POST(p"/alert/$alertId/markAsRead")   => alertCtrl.markAsRead(alertId) // Audit ok
    case POST(p"/alert/$alertId/markAsUnread") => alertCtrl.markAsUnread(alertId) // Audit ok
    case POST(p"/alert/$alertId/follow")       => alertCtrl.followAlert(alertId) // Audit ok
    case POST(p"/alert/$alertId/unfollow")     => alertCtrl.unfollowAlert(alertId) // Audit ok
    case POST(p"/alert/$alertId/createCase")   => alertCtrl.createCase(alertId) // Audit ok
    case POST(p"/alert/_search")               => queryExecutor.alert.search
    // PATCH    /alert/_bulk                         controllers.AlertCtrl.bulkUpdate
    case POST(p"/alert/delete/_bulk")           => alertCtrl.bulkDelete
    case POST(p"/alert/_stats")                 => queryExecutor.alert.stats
    case DELETE(p"/alert/$alertId")             => alertCtrl.delete(alertId) // Audit ok
    case POST(p"/alert/$alertId/merge/$caseId") => alertCtrl.mergeWithCase(alertId, caseId) // Audit ok
    case POST(p"/alert/merge/_bulk")            => alertCtrl.bulkMergeWithCase

    case GET(p"/dashboard")                 => queryExecutor.dashboard.search
    case POST(p"/dashboard/_search")        => queryExecutor.dashboard.search
    case POST(p"/dashboard/_stats")         => queryExecutor.dashboard.stats
    case POST(p"/dashboard")                => dashboardCtrl.create // Audit ok
    case GET(p"/dashboard/$dashboardId")    => dashboardCtrl.get(dashboardId)
    case PATCH(p"/dashboard/$dashboardId")  => dashboardCtrl.update(dashboardId) // Audit ok
    case DELETE(p"/dashboard/$dashboardId") => dashboardCtrl.delete(dashboardId) // Audit ok

    case GET(p"/audit")                      => auditCtrl.flow(None)
    case GET(p"/flow" ? q_o"rootId=$rootId") => auditCtrl.flow(rootId)
    case GET(p"/audit")                      => queryExecutor.audit.search
    case POST(p"/audit/_search")             => queryExecutor.audit.search
    case POST(p"/audit/_stats")              => queryExecutor.audit.stats

    case POST(p"/stream")          => streamCtrl.create
    case GET(p"/stream/status")    => streamCtrl.status
    case GET(p"/stream/$streamId") => streamCtrl.get(streamId)

    case GET(p"/datastore/$id" ? q_o"name=$name")    => attachmentCtrl.download(id, name)
    case GET(p"/datastorezip/$id" ? q_o"name=$name") => attachmentCtrl.downloadZip(id, name)
    case GET(p"/describe/_all")                      => describeCtrl.describeAll
    case GET(p"/describe/$modelName")                => describeCtrl.describe(modelName)

    case GET(p"/config")                    => configCtrl.list
    case GET(p"/config/$path")              => configCtrl.get(path)
    case PUT(p"/config/$path")              => configCtrl.set(path)
    case GET(p"/config/user/$path")         => configCtrl.userGet(path)
    case PUT(p"/config/user/$path")         => configCtrl.userSet(path)
    case GET(p"/config/organisation/$path") => configCtrl.organisationGet(path)
    case PUT(p"/config/organisation/$path") => configCtrl.organisationSet(path)

    case GET(p"/profile")               => queryExecutor.profile.search
    case POST(p"/profile/_search")      => queryExecutor.profile.search
    case POST(p"/profile/_stats")       => queryExecutor.profile.stats
    case POST(p"/profile")              => profileCtrl.create
    case GET(p"/profile/$profileId")    => profileCtrl.get(profileId)
    case PATCH(p"/profile/$profileId")  => profileCtrl.update(profileId)
    case DELETE(p"/profile/$profileId") => profileCtrl.delete(profileId)

    case POST(p"/tag/_import") => tagCtrl.importTaxonomy
    case GET(p"/tag/$id")      => tagCtrl.get(id)
    case POST(p"/tag/_search") => queryExecutor.tag.search
    case POST(p"/tag/_stats")  => queryExecutor.tag.stats

    case GET(p"/page/$idOrTitle")    => pageCtrl.get(idOrTitle)
    case POST(p"/page")              => pageCtrl.create
    case PATCH(p"/page/$idOrTitle")  => pageCtrl.update(idOrTitle)
    case DELETE(p"/page/$idOrTitle") => pageCtrl.delete(idOrTitle)
    case POST(p"/page/_search")      => queryExecutor.page.search
    case POST(p"/page/_stats")       => queryExecutor.page.stats

    case GET(p"/permission") => permissionCtrl.list

    case GET(p"/observable/type")              => queryExecutor.observableType.search
    case GET(p"/observable/type/$idOrName")    => observableTypeCtrl.get(idOrName)
    case POST(p"/observable/type")             => observableTypeCtrl.create
    case DELETE(p"/observable/type/$idOrName") => observableTypeCtrl.delete(idOrName)
  }
}
