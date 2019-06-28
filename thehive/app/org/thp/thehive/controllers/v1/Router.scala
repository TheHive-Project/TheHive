package org.thp.thehive.controllers.v1

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
    customFieldCtrl: CustomFieldCtrl,
    alertCtrl: AlertCtrl,
    auditCtrl: AuditCtrl,
    statusCtrl: StatusCtrl,
    authenticationCtrl: AuthenticationCtrl
) extends SimpleRouter {

  override def routes: Routes = {
    case GET(p"/status") => statusCtrl.get
//    GET  /health                              controllers.StatusCtrl.health
//    GET      /logout                              controllers.AuthenticationCtrl.logout()
    case POST(p"/login") => authenticationCtrl.login()
//    POST     /ssoLogin                            controllers.AuthenticationCtrl.ssoLogin()

//    case GET(p"/case")                  ⇒ caseCtrl.list
    case POST(p"/case")                 => caseCtrl.create
    case GET(p"/case/$caseId")          => caseCtrl.get(caseId)
    case PATCH(p"/case/$caseId")        => caseCtrl.update(caseId)
    case POST(p"/case/_merge/$caseIds") => caseCtrl.merge(caseIds)
    case DELETE(p"/case/$caseId")       => caseCtrl.delete(caseId)
//    case PATCH(p"api/case/_bulk") ⇒                          caseCtrl.bulkUpdate()
//    case POST(p"/case/_stats") ⇒                        caseCtrl.stats()
//    case DELETE(p"/case/$caseId/force") ⇒                  caseCtrl.realDelete(caseId)
//    case GET(p"/case/$caseId/links") ⇒                  caseCtrl.linkedCases(caseId)

    case GET(p"/caseTemplate")                   => caseTemplateCtrl.list
    case POST(p"/caseTemplate")                  => caseTemplateCtrl.create
    case GET(p"/caseTemplate/$caseTemplateId")   => caseTemplateCtrl.get(caseTemplateId)
    case PATCH(p"/caseTemplate/$caseTemplateId") => caseTemplateCtrl.update(caseTemplateId)
    //case DELETE(p"/caseTemplate/$caseTemplateId") ⇒ caseTemplateCtrl.delete(caseTemplateId)

//    case GET(p"/user")           ⇒ userCtrl.list
    case POST(p"/user")          => userCtrl.create
    case GET(p"/user/current")   => userCtrl.current
    case GET(p"/user/$userId")   => userCtrl.get(userId)
    case PATCH(p"/user/$userId") => userCtrl.update(userId)
    //    case DELETE(p"/user/$userId") =>                         userCtrl.delete(userId)
    case POST(p"/user/$userId/password/set")    => userCtrl.setPassword(userId)
    case POST(p"/user/$userId/password/change") => userCtrl.changePassword(userId)
    case GET(p"/user/$userId/key")              => userCtrl.getKey(userId)
    case DELETE(p"/user/$userId/key")           => userCtrl.removeKey(userId)
    case POST(p"/user/$userId/key/renew")       => userCtrl.renewKey(userId)

//    case GET(p"/organisation")                   ⇒ organisationCtrl.list
    case POST(p"/organisation")                  => organisationCtrl.create
    case GET(p"/organisation/$organisationId")   => organisationCtrl.get(organisationId)
    case PATCH(p"/organisation/$organisationId") => organisationCtrl.update(organisationId)

//    case GET(p"/share")            ⇒ shareCtrl.list
//    case POST(p"/share")           ⇒ shareCtrl.create
//    case GET(p"/share/$shareId")   ⇒ shareCtrl.get(shareId)
//    case PATCH(p"/share/$shareId") ⇒ shareCtrl.update(shareId)

    case GET(p"/task")           => taskCtrl.list
    case POST(p"/task")          => taskCtrl.create
    case GET(p"/task/$taskId")   => taskCtrl.get(taskId)
    case PATCH(p"/task/$taskId") => taskCtrl.update(taskId)
    // POST     /case/:caseId/task/_search           controllers.TaskCtrl.findInCase(caseId)
    // POST     /case/task/_stats                    controllers.TaskCtrl.stats()

    case GET(p"/customField")  => customFieldCtrl.list
    case POST(p"/customField") => customFieldCtrl.create

//    case GET(p"/alert")                    ⇒ alertCtrl.list
    case POST(p"/alert")                   => alertCtrl.create
    case GET(p"/alert/$alertId")           => alertCtrl.get(alertId)
    case PATCH(p"/alert/$alertId")         => alertCtrl.update(alertId)
    case POST(p"/alert/$alertId/read")     => alertCtrl.markAsRead(alertId)
    case POST(p"/alert/$alertId/unread")   => alertCtrl.markAsUnread(alertId)
    case POST(p"/alert/$alertId/follow")   => alertCtrl.followAlert(alertId)
    case POST(p"/alert/$alertId/unfollow") => alertCtrl.unfollowAlert(alertId)
    case POST(p"/alert/$alertId/case")     => alertCtrl.createCase(alertId)
    // PATCH    /alert/_bulk                         controllers.AlertCtrl.bulkUpdate()
    //POST     /alert/_stats                        controllers.AlertCtrl.stats()
//    DELETE   /alert/:alertId                      controllers.AlertCtrl.delete(alertId)
//    POST     /alert/:alertId/merge/:caseId        controllers.AlertCtrl.mergeWithCase(alertId, caseId)

    case GET(p"/audit") => auditCtrl.flow()
//      GET      /flow                                controllers.AuditCtrl.flow(rootId: Option[String], count: Option[Int])
//    GET      /audit                               controllers.AuditCtrl.find()
//    POST     /audit/_search                       controllers.AuditCtrl.find()
//    POST     /audit/_stats                        controllers.AuditCtrl.stats()

  }
}
/*

POST     /case/artifact/_search               controllers.ArtifactCtrl.find()
POST     /case/:caseId/artifact/_search       controllers.ArtifactCtrl.findInCase(caseId)
POST     /case/artifact/_stats                controllers.ArtifactCtrl.stats()
POST     /case/:caseId/artifact               controllers.ArtifactCtrl.create(caseId)
GET      /case/artifact/:artifactId           controllers.ArtifactCtrl.get(artifactId)
DELETE   /case/artifact/:artifactId           controllers.ArtifactCtrl.delete(artifactId)
PATCH    /case/artifact/_bulk                 controllers.ArtifactCtrl.bulkUpdate()
PATCH    /case/artifact/:artifactId           controllers.ArtifactCtrl.update(artifactId)
GET      /case/artifact/:artifactId/similar   controllers.ArtifactCtrl.findSimilar(artifactId)


GET      /case/task/:taskId/log               controllers.LogCtrl.findInTask(taskId)
POST     /case/task/:taskId/log/_search       controllers.LogCtrl.findInTask(taskId)
POST     /case/task/log/_search               controllers.LogCtrl.find()
POST     /case/task/log/_stats                controllers.LogCtrl.stats()
POST     /case/task/:taskId/log               controllers.LogCtrl.create(taskId)
PATCH    /case/task/log/:logId                controllers.LogCtrl.update(logId)
DELETE   /case/task/log/:logId                controllers.LogCtrl.delete(logId)
GET      /case/task/log/:logId                controllers.LogCtrl.get(logId)



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




POST     /stream                              controllers.StreamCtrl.create()
GET      /stream/status                       controllers.StreamCtrl.status
GET      /stream/:streamId                    controllers.StreamCtrl.get(streamId)

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
