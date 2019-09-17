package org.thp.thehive.connector.cortex.controllers.v0

import java.util.zip.ZipFile

import scala.util.{Failure, Success}

import play.api.Logger
import play.api.libs.json.{JsFalse, JsObject, JsTrue}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.cortex.dto.v0.{InputReportTemplate, OutputReportTemplate}
import org.thp.scalligraph.controllers.{EntryPoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.thehive.connector.cortex.models.ReportTemplate
import org.thp.thehive.connector.cortex.services.{ReportTemplateSrv, ReportTemplateSteps}
import org.thp.thehive.controllers.v0.{IdOrName, OutputParam, QueryableCtrl}
import org.thp.thehive.models.Permissions

@Singleton
class ReportTemplateCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    reportTemplateSrv: ReportTemplateSrv
) extends QueryableCtrl {

  import ReportTemplateConversion._

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "reportTemplate"
  override val publicProperties: List[PublicProperty[_, _]] = reportTemplateProperties
  override val initialQuery: Query =
    Query.init[ReportTemplateSteps]("listReportTemplate", (graph, _) => reportTemplateSrv.initSteps(graph))
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, ReportTemplateSteps](
    "getReportTemplace",
    FieldsParser[IdOrName],
    (param, graph, _) => reportTemplateSrv.get(param.idOrName)(graph)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, ReportTemplateSteps, PagedResult[ReportTemplate with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, ReportTemplateSteps, _) => ReportTemplateSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[ReportTemplate with Entity, OutputReportTemplate]

  def get(id: String): Action[AnyContent] =
    entryPoint("get content")
      .authRoTransaction(db) { _ => implicit graph =>
        reportTemplateSrv
          .getOrFail(id)
          .map(report => Results.Ok(report.content))
      }

  def importTemplates: Action[AnyContent] =
    entryPoint("import templates")
      .extract("archive", FieldsParser.file.on("templates"))
      .auth { implicit request =>
        val archive: FFile = request.body("archive")
        val triedTemplates = reportTemplateSrv
          .importZipFile(db, new ZipFile(archive.filepath.toFile))
          .map {
            case (analyzerId, Success(_)) => analyzerId -> JsTrue
            case (analyzerId, Failure(e)) =>
              logger.error(s"Import of report template $analyzerId fails", e)
              analyzerId -> JsFalse
          }

        Success(Results.Ok(JsObject(triedTemplates)))
      }

  def create: Action[AnyContent] =
    entryPoint("create template")
      .extract("template", FieldsParser[InputReportTemplate])
      .authTransaction(db) { implicit request => implicit graph =>
        if (request.permissions.contains(Permissions.manageReportTemplate)) {
          val template: InputReportTemplate = request.body("template")
          reportTemplateSrv.create(template).map { createdReportTemplate =>
            Results.Created(createdReportTemplate.toJson)
          }
        } else Success(Results.Unauthorized)
      }

  def delete(id: String): Action[AnyContent] =
    entryPoint("delete template")
      .authTransaction(db) { implicit request => implicit graph =>
        if (request.permissions.contains(Permissions.manageReportTemplate)) {
          reportTemplateSrv
            .get(id)
            .getOrFail()
            .map { reportTemplate =>
              reportTemplateSrv.remove(reportTemplate)
              Results.NoContent
            }
        } else Success(Results.Unauthorized)
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
