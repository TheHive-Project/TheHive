package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

@Singleton
class Router @Inject() (
    alertCtrl: AlertCtrl,
    attachmentCtrl: AttachmentCtrl,
    auditCtrl: AuditCtrl,
    authenticationCtrl: AuthenticationCtrl,
    caseCtrl: CaseCtrl,
    caseTemplateCtrl: CaseTemplateCtrl,
    configCtrl: ConfigCtrl,
    customFieldCtrl: CustomFieldCtrl,
    dashboardCtrl: DashboardCtrl,
    describeCtrl: DescribeCtrl,
    listCtrl: ListCtrl,
    logCtrl: LogCtrl,
    observableCtrl: ObservableCtrl,
    observableTypeCtrl: ObservableTypeCtrl,
    organisationCtrl: OrganisationCtrl,
    pageCtrl: PageCtrl,
    permissionCtrl: PermissionCtrl,
    profileCtrl: ProfileCtrl,
    shareCtrl: ShareCtrl,
    statsCtrl: StatsCtrl,
    statusCtrl: StatusCtrl,
    tagCtrl: TagCtrl,
    taskCtrl: TaskCtrl,
    streamCtrl: StreamCtrl,
    userCtrl: UserCtrl
) extends SimpleRouter {

  override def routes: Routes = {
    case POST(p"/case/$caseId/shares")                         => shareCtrl.shareCase(caseId)
    case GET(p"/case/$caseId/shares")                          => shareCtrl.listShareCases(caseId)
    case DELETE(p"/case/$caseId/shares")                       => shareCtrl.removeShares(caseId)
    case DELETE(p"/case/shares")                               => shareCtrl.removeShares()
    case POST(p"/case/task/$taskId/shares")                    => shareCtrl.shareTask(taskId)
    case GET(p"/case/$caseId/task/$taskId/shares")             => shareCtrl.listShareTasks(caseId, taskId)
    case DELETE(p"/task/$taskId/shares")                       => shareCtrl.removeTaskShares(taskId)
    case POST(p"/case/artifact/$observableId/shares")          => shareCtrl.shareObservable(observableId)
    case GET(p"/case/$caseId/observable/$observableId/shares") => shareCtrl.listShareObservables(caseId, observableId)
    case DELETE(p"/observable/$observableId/shares")           => shareCtrl.removeObservableShares(observableId)
    case DELETE(p"/case/share/$shareId")                       => shareCtrl.removeShare(shareId)
    case PATCH(p"/case/share/$shareId")                        => shareCtrl.updateShare(shareId)

    case GET(p"/alert")                         => alertCtrl.search
    case POST(p"/alert/_search")                => alertCtrl.search
    case POST(p"/alert/_stats")                 => alertCtrl.stats
    case POST(p"/alert")                        => alertCtrl.create
    case GET(p"/alert/$alertId")                => alertCtrl.get(alertId)
    case PATCH(p"/alert/$alertId")              => alertCtrl.update(alertId)
    case POST(p"/alert/delete/_bulk")           => alertCtrl.bulkDelete
    case DELETE(p"/alert/$alertId")             => alertCtrl.delete(alertId)
    case POST(p"/alert/merge/_bulk")            => alertCtrl.bulkMergeWithCase
    case POST(p"/alert/$alertId/merge/$caseId") => alertCtrl.mergeWithCase(alertId, caseId)
    case POST(p"/alert/$alertId/markAsRead")    => alertCtrl.markAsRead(alertId)
    case POST(p"/alert/$alertId/markAsUnread")  => alertCtrl.markAsUnread(alertId)
    case POST(p"/alert/$alertId/follow")        => alertCtrl.followAlert(alertId)
    case POST(p"/alert/$alertId/unfollow")      => alertCtrl.unfollowAlert(alertId)
    case POST(p"/alert/$alertId/createCase")    => alertCtrl.createCase(alertId)
    // PATCH    /alert/_bulk                         controllers.AlertCtrl.bulkUpdate

    case GET(p"/datastore/$id" ? q_o"name=$name")    => attachmentCtrl.download(id, name)
    case GET(p"/datastorezip/$id" ? q_o"name=$name") => attachmentCtrl.downloadZip(id, name)

    case GET(p"/audit")                      => auditCtrl.search
    case POST(p"/audit/_search")             => auditCtrl.search
    case POST(p"/audit/_stats")              => auditCtrl.stats
    case GET(p"/audit")                      => auditCtrl.flow(None)
    case GET(p"/flow" ? q_o"rootId=$rootId") => auditCtrl.flow(rootId)

    case POST(p"/login")  => authenticationCtrl.login
    case GET(p"/logout")  => authenticationCtrl.logout
    case POST(p"/logout") => authenticationCtrl.logout

    case GET(p"/case/template")                    => caseTemplateCtrl.search
    case POST(p"/case/template/_search")           => caseTemplateCtrl.search
    case POST(p"/case/template/_stats")            => caseTemplateCtrl.stats
    case POST(p"/case/template")                   => caseTemplateCtrl.create
    case GET(p"/case/template/$caseTemplateId")    => caseTemplateCtrl.get(caseTemplateId)
    case PATCH(p"/case/template/$caseTemplateId")  => caseTemplateCtrl.update(caseTemplateId)
    case DELETE(p"/case/template/$caseTemplateId") => caseTemplateCtrl.delete(caseTemplateId)

    case POST(p"/case/task/log/_search")  => logCtrl.search
    case POST(p"/case/task/log/_stats")   => logCtrl.stats
    case POST(p"/case/task/$taskId/log")  => logCtrl.create(taskId)
    case PATCH(p"/case/task/log/$logId")  => logCtrl.update(logId)
    case DELETE(p"/case/task/log/$logId") => logCtrl.delete(logId)
    //case GET(p"/case/task/$taskId/log") => logCtrl.findInTask(taskId)
    //case POST(p"/case/task/$taskId/log/_search") => logCtrl.findInTask(taskId)
    //    case GET(p"/case/task/log/$logId") => logCtrl.get(logId)

    case GET(p"/case/task")                  => taskCtrl.search
    case POST(p"/case/task/_search")         => taskCtrl.search
    case POST(p"/case/task/_stats")          => taskCtrl.stats
    case POST(p"/case/$caseId/task")         => taskCtrl.create(caseId)
    case GET(p"/case/task/$taskId")          => taskCtrl.get(taskId)
    case PATCH(p"/case/task/$taskId")        => taskCtrl.update(taskId)
    case POST(p"/case/$caseId/task/_search") => taskCtrl.searchInCase(caseId)

    case POST(p"/case/artifact/_search")              => observableCtrl.search
    case POST(p"/case/artifact/_stats")               => observableCtrl.stats
    case POST(p"/case/$caseId/artifact")              => observableCtrl.createInCase(caseId)
    case GET(p"/case/artifact/$observableId")         => observableCtrl.get(observableId)
    case PATCH(p"/case/artifact/_bulk")               => observableCtrl.bulkUpdate
    case PATCH(p"/case/artifact/$observableId")       => observableCtrl.update(observableId)
    case DELETE(p"/case/artifact/$observableId")      => observableCtrl.delete(observableId)
    case POST(p"/alert/$alertId/artifact")            => observableCtrl.createInAlert(alertId)
    case GET(p"/alert/artifact/$observableId")        => observableCtrl.get(observableId)
    case PATCH(p"/alert/artifact/_bulk")              => observableCtrl.bulkUpdate
    case PATCH(p"/alert/artifact/$observableId")      => observableCtrl.update(observableId)
    case DELETE(p"/alert/artifact/$observableId")     => observableCtrl.delete(observableId)
    case GET(p"/case/artifact/$observableId/similar") => observableCtrl.findSimilar(observableId)
    //    case POST(p"/case/:caseId/artifact/_search")    => observableCtrl.findInCase(caseId)

    case GET(p"/case")                          => caseCtrl.search
    case POST(p"/case")                         => caseCtrl.create         // Audit ok
    case GET(p"/case/$caseId")                  => caseCtrl.get(caseId)
    case PATCH(p"/case/_bulk")                  => caseCtrl.bulkUpdate     // Not used by the frontend
    case PATCH(p"/case/$caseId")                => caseCtrl.update(caseId) // Audit ok
    case POST(p"/case/$caseId/_merge/$toMerge") => caseCtrl.merge(caseId, toMerge)
    case POST(p"/case/_search")                 => caseCtrl.search
    case POST(p"/case/_stats")                  => caseCtrl.stats
    case DELETE(p"/case/$caseId")               => caseCtrl.delete(caseId) // Not used by the frontend
    case DELETE(p"/case/$caseId/force")         => caseCtrl.delete(caseId) // Audit ok
    case GET(p"/case/$caseId/links")            => caseCtrl.linkedCases(caseId)

    case GET(p"/config/user")               => configCtrl.userList
    case GET(p"/config/user/$path")         => configCtrl.userGet(path)
    case PUT(p"/config/user/$path")         => configCtrl.userSet(path)
    case GET(p"/config/organisation")       => configCtrl.organisationList
    case GET(p"/config/organisation/$path") => configCtrl.organisationGet(path)
    case PUT(p"/config/organisation/$path") => configCtrl.organisationSet(path)
    case GET(p"/config")                    => configCtrl.list
    case GET(p"/config/$path")              => configCtrl.get(path)
    case PUT(p"/config/$path")              => configCtrl.set(path)

    case GET(p"/customField")           => customFieldCtrl.list
    case POST(p"/customFields/_search") => customFieldCtrl.search
    case POST(p"/customFields/_stats")  => customFieldCtrl.stats
    case POST(p"/customField")          => customFieldCtrl.create
    case GET(p"/customField/$id")       => customFieldCtrl.get(id)
    case PATCH(p"/customField/$id")     => customFieldCtrl.update(id)
    case DELETE(p"/customField/$id")    => customFieldCtrl.delete(id)
    case GET(p"/customFields/$id/use")  => customFieldCtrl.useCount(id)

    case GET(p"/dashboard")                 => dashboardCtrl.search
    case POST(p"/dashboard/_search")        => dashboardCtrl.search
    case POST(p"/dashboard/_stats")         => dashboardCtrl.stats
    case POST(p"/dashboard")                => dashboardCtrl.create
    case GET(p"/dashboard/$dashboardId")    => dashboardCtrl.get(dashboardId)
    case PATCH(p"/dashboard/$dashboardId")  => dashboardCtrl.update(dashboardId)
    case DELETE(p"/dashboard/$dashboardId") => dashboardCtrl.delete(dashboardId)

    case GET(p"/describe/_all")       => describeCtrl.describeAll
    case GET(p"/describe/$modelName") => describeCtrl.describe(modelName)

    case GET(p"/list")                    => listCtrl.list
    case POST(p"/list/$listName")         => listCtrl.addItem(listName)
    case GET(p"/list/$listName")          => listCtrl.listItems(listName)
    case PATCH(p"/list/$itemId")          => listCtrl.updateItem(itemId)
    case DELETE(p"/list/$itemId")         => listCtrl.deleteItem(itemId)
    case POST(p"/list/$listName/_exists") => listCtrl.itemExists(listName)

    case GET(p"/observable/type")              => observableTypeCtrl.search
    case POST(p"/observable/type/_search")     => observableTypeCtrl.search
    case POST(p"/observable/type/_stats")      => observableTypeCtrl.stats
    case POST(p"/observable/type")             => observableTypeCtrl.create
    case GET(p"/observable/type/$idOrName")    => observableTypeCtrl.get(idOrName)
    case DELETE(p"/observable/type/$idOrName") => observableTypeCtrl.delete(idOrName)

    case GET(p"/organisation")                                           => organisationCtrl.list
    case POST(p"/organisation/_search")                                  => organisationCtrl.search
    case POST(p"/organisation/_stats")                                   => organisationCtrl.stats
    case POST(p"/organisation")                                          => organisationCtrl.create
    case GET(p"/organisation/$organisationId")                           => organisationCtrl.get(organisationId)
    case PATCH(p"/organisation/$organisationId")                         => organisationCtrl.update(organisationId)
    case PUT(p"/organisation/$organisationId1/link/$organisationId2")    => organisationCtrl.link(organisationId1, organisationId2)
    case PUT(p"/organisation/$organisationId/links")                     => organisationCtrl.bulkLink(organisationId)
    case GET(p"/organisation/$organisationId/links")                     => organisationCtrl.listLinks(organisationId)
    case DELETE(p"/organisation/$organisationId1/link/$organisationId2") => organisationCtrl.unlink(organisationId1, organisationId2)

    case POST(p"/page/_search")      => pageCtrl.search
    case POST(p"/page/_stats")       => pageCtrl.stats
    case POST(p"/page")              => pageCtrl.create
    case GET(p"/page/$idOrTitle")    => pageCtrl.get(idOrTitle)
    case PATCH(p"/page/$idOrTitle")  => pageCtrl.update(idOrTitle)
    case DELETE(p"/page/$idOrTitle") => pageCtrl.delete(idOrTitle)

    case GET(p"/permission") => permissionCtrl.list

    case GET(p"/profile")               => profileCtrl.search
    case POST(p"/profile/_search")      => profileCtrl.search
    case POST(p"/profile/_stats")       => profileCtrl.stats
    case POST(p"/profile")              => profileCtrl.create
    case GET(p"/profile/$profileId")    => profileCtrl.get(profileId)
    case PATCH(p"/profile/$profileId")  => profileCtrl.update(profileId)
    case DELETE(p"/profile/$profileId") => profileCtrl.delete(profileId)

    case POST(p"/_stats") => statsCtrl.stats

    case GET(p"/status") => statusCtrl.get
    case GET(p"/health") => statusCtrl.health

    case POST(p"/stream")          => streamCtrl.create
    case GET(p"/stream/status")    => streamCtrl.status
    case GET(p"/stream/$streamId") => streamCtrl.get(streamId)

    case GET(p"/tag")          => tagCtrl.search
    case POST(p"/tag/_search") => tagCtrl.search
    case POST(p"/tag/_stats")  => tagCtrl.stats
    case POST(p"/tag/_import") => tagCtrl.importTaxonomy
    case GET(p"/tag/$id")      => tagCtrl.get(id)

    case GET(p"/user")                          => userCtrl.search
    case POST(p"/user/_search")                 => userCtrl.search
    case POST(p"/user/_stats")                  => userCtrl.stats
    case POST(p"/user")                         => userCtrl.create
    case GET(p"/user/current")                  => userCtrl.current
    case GET(p"/user/$userId")                  => userCtrl.get(userId)
    case PATCH(p"/user/$userId")                => userCtrl.update(userId)
    case DELETE(p"/user/$userId")               => userCtrl.lock(userId)
    case DELETE(p"/user/$userId/force")         => userCtrl.delete(userId)
    case POST(p"/user/$userId/password/set")    => userCtrl.setPassword(userId)
    case POST(p"/user/$userId/password/change") => userCtrl.changePassword(userId)
    case GET(p"/user/$userId/key")              => userCtrl.getKey(userId)
    case DELETE(p"/user/$userId/key")           => userCtrl.removeKey(userId)
    case POST(p"/user/$userId/key/renew")       => userCtrl.renewKey(userId)
  }
}
