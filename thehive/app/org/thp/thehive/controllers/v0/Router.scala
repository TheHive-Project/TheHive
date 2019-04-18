package org.thp.thehive.controllers.v0

import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

import javax.inject.{Inject, Singleton}

@Singleton
class Router @Inject()(
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
    authenticationCtrl: AuthenticationCtrl,
    listCtrl: ListCtrl,
    streamCtrl: StreamCtrl)
    extends SimpleRouter {

  override def routes: Routes = {

    /**/
    case GET(p"/status") ⇒ statusCtrl.get
//    GET  /health                              controllers.StatusCtrl.health
//    GET      /logout                              controllers.AuthenticationCtrl.logout()
    case POST(p"/login") ⇒ authenticationCtrl.login()
//    POST     /ssoLogin                            controllers.AuthenticationCtrl.ssoLogin()

    /**/
    case GET(p"/case")                  ⇒ caseCtrl.list
    case POST(p"/case")                 ⇒ caseCtrl.create
    case GET(p"/case/$caseId")          ⇒ caseCtrl.get(caseId)
    case PATCH(p"/case/$caseId")        ⇒ caseCtrl.update(caseId)
    case POST(p"/case/_merge/$caseIds") ⇒ caseCtrl.merge(caseIds)
    case DELETE(p"/case/$caseId")       ⇒ caseCtrl.delete(caseId)
    case POST(p"/case/_search")         ⇒ caseCtrl.search
//    case PATCH(p"api/case/_bulk") ⇒                          caseCtrl.bulkUpdate()
    case POST(p"/case/_stats") ⇒ caseCtrl.stats()
//    case DELETE(p"/case/$caseId/force") ⇒                  caseCtrl.realDelete(caseId)
    case GET(p"/case/$caseId/links") ⇒ caseCtrl.linkedCases(caseId)

    case GET(p"/case/template")                   ⇒ caseTemplateCtrl.list
    case POST(p"/case/template")                  ⇒ caseTemplateCtrl.create
    case GET(p"/case/template/$caseTemplateId")   ⇒ caseTemplateCtrl.get(caseTemplateId)
    case PATCH(p"/case/template/$caseTemplateId") ⇒ caseTemplateCtrl.update(caseTemplateId)
    case POST(p"/case/template/_search")          ⇒ caseTemplateCtrl.search
    //case DELETE(p"/caseTemplate/$caseTemplateId") ⇒ caseTemplateCtrl.delete(caseTemplateId)

    case GET(p"/user")           ⇒ userCtrl.list
    case POST(p"/user")          ⇒ userCtrl.create
    case GET(p"/user/current")   ⇒ userCtrl.current
    case GET(p"/user/$userId")   ⇒ userCtrl.get(userId)
    case PATCH(p"/user/$userId") ⇒ userCtrl.update(userId)
    //    case DELETE(p"/user/$userId") =>                         userCtrl.delete(userId)
    case POST(p"/user/$userId/password/set")    ⇒ userCtrl.setPassword(userId)
    case POST(p"/user/$userId/password/change") ⇒ userCtrl.changePassword(userId)
    case GET(p"/user/$userId/key")              ⇒ userCtrl.getKey(userId)
    case DELETE(p"/user/$userId/key")           ⇒ userCtrl.removeKey(userId)
    case POST(p"/user/$userId/key/renew")       ⇒ userCtrl.renewKey(userId)

    case GET(p"/list")                    ⇒ listCtrl.list()
    case DELETE(p"/list/$itemId")         ⇒ listCtrl.deleteItem(itemId)
    case PATCH(p"/list/$itemId")          ⇒ listCtrl.updateItem(itemId)
    case POST(p"/list/$listName")         ⇒ listCtrl.addItem(listName)
    case GET(p"/list/$listName")          ⇒ listCtrl.listItems(listName)
    case POST(p"/list/$listName/_exists") ⇒ listCtrl.itemExists(listName)

    case GET(p"/organisation")                   ⇒ organisationCtrl.list
    case POST(p"/organisation")                  ⇒ organisationCtrl.create
    case GET(p"/organisation/$organisationId")   ⇒ organisationCtrl.get(organisationId)
    case PATCH(p"/organisation/$organisationId") ⇒ organisationCtrl.update(organisationId)

//    case GET(p"/share")            ⇒ shareCtrl.list
//    case POST(p"/share")           ⇒ shareCtrl.create
//    case GET(p"/share/$shareId")   ⇒ shareCtrl.get(shareId)
//    case PATCH(p"/share/$shareId") ⇒ shareCtrl.update(shareId)

    case GET(p"/case/task")           ⇒ taskCtrl.list
    case POST(p"/case/task")          ⇒ taskCtrl.create
    case GET(p"/case/task/$taskId")   ⇒ taskCtrl.get(taskId)
    case PATCH(p"/case/task/$taskId") ⇒ taskCtrl.update(taskId)
    case POST(p"/case/task/_search")  ⇒ taskCtrl.search
    //case POST(p"/case/$caseId/task/_search") => taskCtrl.search
    case POST(p"/case/task/_stats") ⇒ taskCtrl.stats()

//case GET(p"/case/task/$taskId/log") => logCtrl.findInTask(taskId)
//case POST(p"/case/task/$taskId/log/_search") => logCtrl.findInTask(taskId)
    case POST(p"/case/task/log/_search") ⇒ logCtrl.search()
    case POST(p"/case/task/log/_stats")  ⇒ logCtrl.stats()
    case POST(p"/case/task/$taskId/log") ⇒ logCtrl.create(taskId)
//    case PATCH(p"/case/task/log/$logId") => logCtrl.update(logId)
//    case DELETE(p"/case/task/log/$logId") => logCtrl.delete(logId)
//    case GET(p"/case/task/log/$logId") => logCtrl.get(logId)

    case POST(p"/case/artifact/_search") ⇒ observableCtrl.search()
//    case POST(p"/case/:caseId/artifact/_search")    ⇒ observableCtrl.findInCase(caseId)
    case POST(p"/case/artifact/_stats")       ⇒ observableCtrl.stats()
    case POST(p"/case/$caseId/artifact")      ⇒ observableCtrl.create(caseId)
    case GET(p"/case/artifact/$observableId") ⇒ observableCtrl.get(observableId)
//    case DELETE(p"/case/artifact/$observableId")      ⇒ observableCtrl.delete(observableId)
//    case PATCH(p"/case/artifact/_bulk")             ⇒ observableCtrl.bulkUpdate()
    case PATCH(p"/case/artifact/$observableId") ⇒ observableCtrl.update(observableId)
//    case GET(p"/case/artifact/$observableId/similar") ⇒ observableCtrl.findSimilar(observableId)

    case GET(p"/customField")  ⇒ customFieldCtrl.list
    case POST(p"/customField") ⇒ customFieldCtrl.create

    case GET(p"/alert")                    ⇒ alertCtrl.list
    case POST(p"/alert")                   ⇒ alertCtrl.create
    case GET(p"/alert/$alertId")           ⇒ alertCtrl.get(alertId)
    case PATCH(p"/alert/$alertId")         ⇒ alertCtrl.update(alertId)
    case POST(p"/alert/$alertId/read")     ⇒ alertCtrl.markAsRead(alertId)
    case POST(p"/alert/$alertId/unread")   ⇒ alertCtrl.markAsUnread(alertId)
    case POST(p"/alert/$alertId/follow")   ⇒ alertCtrl.followAlert(alertId)
    case POST(p"/alert/$alertId/unfollow") ⇒ alertCtrl.unfollowAlert(alertId)
    case POST(p"/alert/$alertId/case")     ⇒ alertCtrl.createCase(alertId)
    case POST(p"/alert/_search")           ⇒ alertCtrl.search
    // PATCH    /alert/_bulk                         controllers.AlertCtrl.bulkUpdate()
    case POST(p"/alert/_stats") ⇒ alertCtrl.stats()
//    DELETE   /alert/:alertId                      controllers.AlertCtrl.delete(alertId)
//    POST     /alert/:alertId/merge/:caseId        controllers.AlertCtrl.mergeWithCase(alertId, caseId)

    case GET(p"/audit") ⇒ auditCtrl.flow()
//      GET      /flow                                controllers.AuditCtrl.flow(rootId: Option[String], count: Option[Int])
//    GET      /audit                               controllers.AuditCtrl.find()
//    POST     /audit/_search                       controllers.AuditCtrl.find()
//    POST     /audit/_stats                        controllers.AuditCtrl.stats()

    case POST(p"/stream")          ⇒ streamCtrl.create()
    case GET(p"/stream/status")    ⇒ streamCtrl.status
    case GET(p"/stream/$streamId") ⇒ streamCtrl.get(streamId)

  }
}
/*






GET      /datastore/:hash                     controllers.AttachmentCtrl.download(hash, name: Option[String])
GET      /datastorezip/:hash                  controllers.AttachmentCtrl.downloadZip(hash, name: Option[String])

POST     /maintenance/migrate                 org.elastic4play.controllers.MigrationCtrl.migrate
#POST          /maintenance/rehash                         controllers.MaintenanceCtrl.reHash

GET      /list                                org.elastic4play.controllers.DBListCtrl.list()
DELETE   /list/:itemId                        org.elastic4play.controllers.DBListCtrl.deleteItem(itemId)
PATCH    /list/:itemId                        org.elastic4play.controllers.DBListCtrl.updateItem(itemId)
POST     /list/:listName                      org.elastic4play.controllers.DBListCtrl.addItem(listName)
GET      /list/:listName                      org.elastic4play.controllers.DBListCtrl.listItems(listName)
POST     /list/:listName/_exists              org.elastic4play.controllers.DBListCtrl.itemExists(listName)





GET      /describe/_all                       controllers.DescribeCtrl.describeAll
GET      /describe/:modelName                 controllers.DescribeCtrl.describe(modelName)

GET      /dashboard                           controllers.DashboardCtrl.find()
POST     /dashboard/_search                   controllers.DashboardCtrl.find()
POST     /dashboard/_stats                    controllers.DashboardCtrl.stats()
POST     /dashboard                           controllers.DashboardCtrl.create()
GET      /dashboard/:dashboardId              controllers.DashboardCtrl.get(dashboardId)
PATCH    /dashboard/:dashboardId              controllers.DashboardCtrl.update(dashboardId)
DELETE   /dashboard/:dashboardId              controllers.DashboardCtrl.delete(dashboardId)

->       /connector                           connectors.ConnectorRouter

GET      / *file                                   controllers.AssetCtrl.get(file)

 */
