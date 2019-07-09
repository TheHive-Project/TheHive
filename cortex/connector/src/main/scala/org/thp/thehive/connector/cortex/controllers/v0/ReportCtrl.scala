package org.thp.thehive.connector.cortex.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.models.ReportType
import org.thp.thehive.connector.cortex.services.ReportTemplateSrv
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Success, Try}

@Singleton
class ReportCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    reportTemplateSrv: ReportTemplateSrv
) {
  import ReportTemplateConversion._

  def getContent(analyzerId: String, reportType: String): Action[AnyContent] =
    entryPoint("get content")
      .authTransaction(db) { implicit request => implicit graph =>
        {
          for {
            rType    <- Try(ReportType.withName(reportType))
            template <- reportTemplateSrv.initSteps.forWorkerAndType(analyzerId, rType).getOrFail()
          } yield Results.Ok(template.toJson)
        } orElse Success(Results.NotFound)
      }
}
