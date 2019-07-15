package org.thp.thehive.connector.cortex.controllers.v0

import java.util.zip.ZipFile

import javax.inject.{Inject, Singleton}
import org.thp.cortex.dto.v0.{InputReportTemplate, OutputReportTemplate}
import org.thp.scalligraph.controllers.{EntryPoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.thehive.connector.cortex.models.{ReportTemplate, ReportType}
import org.thp.thehive.connector.cortex.services.{ReportTemplateSrv, ReportTemplateSteps}
import org.thp.thehive.controllers.v0.QueryableCtrl
import org.thp.thehive.models.Permissions
import play.api.libs.json.{JsFalse, JsObject, JsTrue}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Success, Try}

@Singleton
class ReportCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    reportTemplateSrv: ReportTemplateSrv
) extends QueryableCtrl {

  import ReportTemplateConversion._

  override val entityName: String                           = "report"
  override val publicProperties: List[PublicProperty[_, _]] = reportTemplateProperties
  override val initialQuery: ParamQuery[_] =
    Query.init[ReportTemplateSteps]("listReportTemplate", (graph, _) => reportTemplateSrv.initSteps(graph))
  override val pageQuery: ParamQuery[_] = Query.withParam[RangeParams, ReportTemplateSteps, PagedResult[ReportTemplate with Entity]](
    "page",
    FieldsParser[RangeParams],
    (range, ReportTemplateSteps, _) => ReportTemplateSteps.page(range.from, range.to, range.withSize.getOrElse(false))
  )
  override val outputQuery: ParamQuery[_] = Query.output[ReportTemplate with Entity, OutputReportTemplate]

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

  def get(id: String): Action[AnyContent] =
    entryPoint("get template")
      .authTransaction(db) { _ => implicit graph =>
        {
          for {
            template <- reportTemplateSrv.get(id).getOrFail()
          } yield Results.Ok(template.toJson)
        } recover { case e => Results.NotFound(e.getMessage) }
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

  def create: Action[AnyContent] =
    entryPoint("create template")
      .extract("template", FieldsParser[InputReportTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        // FIXME is there a need for ACL check concerning ReportTemplates? If so how to check it without any Edge
        if (request.permissions.contains(Permissions.manageReportTemplate)) {
          val template: InputReportTemplate = request.body("template")

          {
            for {
              reportType <- Try(ReportType.withName(template.reportType))
              _ <- reportTemplateSrv
                .initSteps
                .forWorkerAndType(template.analyzerId, reportType)
                .getOrFail()
            } yield Results.Conflict
          } orElse Try(reportTemplateSrv.create(template))
            .map(t => Results.Created(t.toJson))
        } else Success(Results.Unauthorized)
      }

  def delete(id: String): Action[AnyContent] =
    entryPoint("delete template")
      .authTransaction(db) { _ => implicit graph =>
        Try(
          reportTemplateSrv
            .get(id)
            .remove()
        ).map(_ => Results.Ok)
          .recover { case e => Results.InternalServerError(e.getMessage) }
      }

  def update(id: String): Action[AnyContent] =
    entryPoint("update template")
      .extract("template", FieldsParser.update("template", reportTemplateProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        if (request.permissions.contains(Permissions.manageReportTemplate)) {
          val propertyUpdaters: Seq[PropertyUpdater] = request.body("template")

          for {
            (templateSteps, _) <- reportTemplateSrv.update(_.get(id), propertyUpdaters)
            template           <- templateSteps.getOrFail()
          } yield Results.Ok(template.toJson)

        } else Success(Results.Unauthorized)
      }
}
