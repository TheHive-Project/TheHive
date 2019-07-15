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
    reportCtrl: ReportCtrl
) extends SimpleRouter {
  override def routes: Routes = {
    case GET(p"/job/$jobId<[^/]*>") => jobCtrl.get(jobId)
    case POST(p"/job/_search")      => cortexQueryExecutor.job.search
    case POST(p"/job/_stats")       => cortexQueryExecutor.job.stats
    case POST(p"/job")              => jobCtrl.create

    case GET(p"/analyzer")        => analyzerCtrl.list
    case POST(p"/action/_search") => actionCtrl.list

    case GET(p"/report/template/content/$analyzerId<[^/]*>/$reportType<[^/]*>") => reportCtrl.getContent(analyzerId, reportType)
    case POST(p"/report/template/_import")                                      => reportCtrl.importTemplates
    case POST(p"/report/template/_search")                                      => cortexQueryExecutor.report.search
    case POST(p"/report/template")                                              => reportCtrl.create()
    case DELETE(p"/report/template/$reportTemplateId<[^/]*>")                   => reportCtrl.delete(reportTemplateId)
    case GET(p"/report/template/$reportTemplateId<[^/]*>")                      => reportCtrl.get(reportTemplateId)
    case PATCH(p"/report/template/$reportTemplateId<[^/]*>")                    => reportCtrl.update(reportTemplateId)
  }
}
