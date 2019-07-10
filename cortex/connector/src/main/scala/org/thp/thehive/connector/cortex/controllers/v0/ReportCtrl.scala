package org.thp.thehive.connector.cortex.controllers.v0

import java.util.zip.ZipFile

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FFile, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.connector.cortex.models.ReportType
import org.thp.thehive.connector.cortex.services.ReportTemplateSrv
import play.api.libs.json.{JsFalse, JsObject, JsTrue}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Success, Try}

@Singleton
class ReportCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    reportTemplateSrv: ReportTemplateSrv
) {

  def getContent(analyzerId: String, reportType: String): Action[AnyContent] =
    entryPoint("get content")
      .authTransaction(db) { _ => implicit graph =>
        {
          for {
            rType    <- Try(ReportType.withName(reportType))
            template <- reportTemplateSrv.initSteps.forWorkerAndType(analyzerId, rType).getOrFail()
          } yield Results.Ok(template.content).as("text/html")
        } orElse Success(Results.NotFound)
      }

  def importTemplates: Action[AnyContent] =
    entryPoint("import templates")
      .extract("archive", FieldsParser.file.on("templates"))
      .authTransaction(db) { implicit req => implicit graph =>
        val archive: FFile = req.body("archive")
        val triedTemplates = reportTemplateSrv.importZipFile(new ZipFile(archive.filepath.toFile))

        val r = triedTemplates
          .map(
            t =>
              t.map(template => s"${template.workerId}_${template.reportType}" -> JsTrue)
                .recover {
                  case e => e.getMessage -> JsFalse
                }
                .get
          )

        Success(Results.Ok(JsObject(r.toSeq)))
      }
}
