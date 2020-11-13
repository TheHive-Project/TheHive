package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

@Singleton
class Router @Inject() (
    val jobCtrl: JobCtrl,
    analyzerCtrl: AnalyzerCtrl,
    val actionCtrl: ActionCtrl,
    val reportCtrl: AnalyzerTemplateCtrl,
    responderCtrl: ResponderCtrl
) extends SimpleRouter {
  override def routes: Routes = {
    case GET(p"/job/$jobId<[^/]*>") => jobCtrl.get(jobId)
    case POST(p"/job/_search")      => jobCtrl.search
    case POST(p"/job/_stats")       => jobCtrl.stats
    case POST(p"/job")              => jobCtrl.create // Audit ok

    case POST(p"/action")                                    => actionCtrl.create // Audit ok
    case POST(p"/action/_search")                            => actionCtrl.search
    case POST(p"/action/_stats")                             => actionCtrl.stats
    case GET(p"/action")                                     => actionCtrl.search
    case GET(p"/action/$entityType<[^/]*>/$entityId<[^/]*>") => actionCtrl.getByEntity(entityType, entityId)

    case GET(p"/analyzer/template/content/$analyzerId<[^/]*>$x*") => reportCtrl.get(analyzerId)
    case POST(p"/analyzer/template/_import")                      => reportCtrl.importTemplates            // Audit ok
    case POST(p"/analyzer/template/_search")                      => reportCtrl.search
    case POST(p"/analyzer/template")                              => reportCtrl.create()                   // Audit ok
    case DELETE(p"/analyzer/template/$analyzerTemplateId<[^/]*>") => reportCtrl.delete(analyzerTemplateId) // Audit ok
    case GET(p"/analyzer/template/$analyzerTemplateId<[^/]*>")    => reportCtrl.get(analyzerTemplateId)
    case PATCH(p"/analyzer/template/$analyzerTemplateId<[^/]*>")  => reportCtrl.update(analyzerTemplateId) // Audit ok

    case GET(p"/report/template/content/$analyzerId<[^/]*>$x*") => reportCtrl.get(analyzerId)
    case POST(p"/report/template/_import")                      => reportCtrl.importTemplates            // Audit ok
    case POST(p"/report/template/_search")                      => reportCtrl.search
    case POST(p"/report/template")                              => reportCtrl.create()                   // Audit ok
    case DELETE(p"/report/template/$analyzerTemplateId<[^/]*>") => reportCtrl.delete(analyzerTemplateId) // Audit ok
    case GET(p"/report/template/$analyzerTemplateId<[^/]*>")    => reportCtrl.get(analyzerTemplateId)
    case PATCH(p"/report/template/$analyzerTemplateId<[^/]*>")  => reportCtrl.update(analyzerTemplateId) // Audit ok

    case GET(p"/analyzer/$analyzerId<[^/]*>")    => analyzerCtrl.getById(analyzerId)
    case GET(p"/analyzer")                       => analyzerCtrl.list
    case GET(p"/analyzer/type/$dataType<[^/]*>") => analyzerCtrl.listByType(dataType)

    case GET(p"/responder/$entityType<[^/]*>/$entityId<[^/]*>") => responderCtrl.getResponders(entityType, entityId)
    case POST(p"/responder/_search")                            => responderCtrl.searchResponders
  }
}
