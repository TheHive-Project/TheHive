package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

@Singleton
class Router @Inject()(
    jobCtrl: JobCtrl,
    analyzerCtrl: AnalyzerCtrl,
    actionCtrl: ActionCtrl,
    cortexQueryExecutor: CortexQueryExecutor,
    reportCtrl: ReportCtrl,
    responderCtrl: ResponderCtrl
) extends SimpleRouter {
  override def routes: Routes = {
    case GET(p"/job/$jobId<[^/]*>") => jobCtrl.get(jobId)
    case POST(p"/job/_search")      => cortexQueryExecutor.job.search
    case POST(p"/job/_stats")       => cortexQueryExecutor.job.stats
    case POST(p"/job")              => jobCtrl.create // Audit ok

    case GET(p"/analyzer/$analyzerId<[^/]*>")    => analyzerCtrl.getById(analyzerId)
    case GET(p"/analyzer")                       => analyzerCtrl.list
    case GET(p"/analyzer/type/$dataType<[^/]*>") => analyzerCtrl.listByType(dataType)

    case POST(p"/action")                                    => actionCtrl.create // Audit ok
    case POST(p"/action/_search")                            => cortexQueryExecutor.action.search
    case POST(p"/action/_stats")                             => cortexQueryExecutor.action.stats
    case GET(p"/action")                                     => cortexQueryExecutor.action.search
    case GET(p"/action/$entityType<[^/]*>/$entityId<[^/]*>") => actionCtrl.getByEntity(entityType, entityId)

    case GET(p"/report/template/content/$analyzerId<[^/]*>/?.*") => reportCtrl.get(analyzerId)
    case POST(p"/report/template/_import")                       => reportCtrl.importTemplates // Audit ok
    case POST(p"/report/template/_search")                       => cortexQueryExecutor.report.search
    case POST(p"/report/template")                               => reportCtrl.create() // Audit ok
    case DELETE(p"/report/template/$reportTemplateId<[^/]*>")    => reportCtrl.delete(reportTemplateId) // Audit ok
    case GET(p"/report/template/$reportTemplateId<[^/]*>")       => reportCtrl.get(reportTemplateId)
    case PATCH(p"/report/template/$reportTemplateId<[^/]*>")     => reportCtrl.update(reportTemplateId) // Audit ok

    case GET(p"/responder/$entityType<[^/]*>/$entityId<[^/]*>") => responderCtrl.getResponders(entityType, entityId)
    case POST(p"/responder/_search")                            => responderCtrl.searchResponders
  }
}
