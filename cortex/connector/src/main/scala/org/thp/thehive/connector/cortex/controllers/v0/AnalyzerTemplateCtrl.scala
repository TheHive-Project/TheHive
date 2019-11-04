package org.thp.thehive.connector.cortex.controllers.v0

import java.util.zip.ZipFile

import scala.util.{Failure, Success}

import play.api.Logger
import play.api.libs.json.{JsFalse, JsObject, JsTrue}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.dto.v0.InputAnalyzerTemplate
import org.thp.thehive.connector.cortex.models.AnalyzerTemplate
import org.thp.thehive.connector.cortex.services.{AnalyzerTemplateSrv, AnalyzerTemplateSteps}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.controllers.v0.{IdOrName, OutputParam, QueryableCtrl}
import org.thp.thehive.models.Permissions

@Singleton
class AnalyzerTemplateCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    properties: Properties,
    analyzerTemplateSrv: AnalyzerTemplateSrv
) extends QueryableCtrl {

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "analyzerTemplate"
  override val publicProperties: List[PublicProperty[_, _]] = properties.analyzerTemplate ::: metaProperties[AnalyzerTemplateSteps]
  override val initialQuery: Query =
    Query.init[AnalyzerTemplateSteps]("listAnalyzerTemplate", (graph, _) => analyzerTemplateSrv.initSteps(graph))
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, AnalyzerTemplateSteps](
    "getReportTemplace",
    FieldsParser[IdOrName],
    (param, graph, _) => analyzerTemplateSrv.get(param.idOrName)(graph)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, AnalyzerTemplateSteps, PagedResult[AnalyzerTemplate with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, AnalyzerTemplateSteps, _) => AnalyzerTemplateSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[AnalyzerTemplate with Entity]()

  def get(id: String): Action[AnyContent] =
    entryPoint("get content")
      .authPermittedTransaction(db, Permissions.manageAnalyzerTemplate) { _ => implicit graph =>
        analyzerTemplateSrv
          .getOrFail(id)
          .map(report => Results.Ok(report.content))
      }

  def importTemplates: Action[AnyContent] =
    entryPoint("import templates")
      .extract("archive", FieldsParser.file.on("templates"))
      .auth { implicit request =>
        val archive: FFile = request.body("archive")
        val triedTemplates = analyzerTemplateSrv
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
      .extract("analyzerTemplate", FieldsParser[InputAnalyzerTemplate])
      .authPermittedTransaction(db, Permissions.manageAnalyzerTemplate) { implicit request => implicit graph =>
        val analyzerTemplate: InputAnalyzerTemplate = request.body("analyzerTemplate")
        analyzerTemplateSrv.create(analyzerTemplate.toAnalyzerTemplate).map { createdAnalyzerTemplate =>
          Results.Created(createdAnalyzerTemplate.toJson)
        }
      }

  def delete(id: String): Action[AnyContent] =
    entryPoint("delete template")
      .authPermittedTransaction(db, Permissions.manageAnalyzerTemplate) { implicit request => implicit graph =>
        analyzerTemplateSrv
          .get(id)
          .getOrFail()
          .map { analyzerTemplate =>
            analyzerTemplateSrv.remove(analyzerTemplate)
            Results.NoContent
          }
      }

  def update(id: String): Action[AnyContent] =
    entryPoint("update template")
      .extract("template", FieldsParser.update("template", properties.analyzerTemplate))
      .authPermittedTransaction(db, Permissions.manageAnalyzerTemplate) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("template")

        for {
          (templateSteps, _) <- analyzerTemplateSrv.update(_.get(id), propertyUpdaters)
          template           <- templateSteps.getOrFail()
        } yield Results.Ok(template.toJson)

      }
}
