package org.thp.thehive.connector.cortex.controllers.v0

import java.util.zip.ZipFile

import com.google.inject.name.Named
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.dto.v0.InputAnalyzerTemplate
import org.thp.thehive.connector.cortex.models.AnalyzerTemplate
import org.thp.thehive.connector.cortex.services.AnalyzerTemplateSrv
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.controllers.v0.{IdOrName, OutputParam, QueryableCtrl}
import org.thp.thehive.models.Permissions
import play.api.Logger
import play.api.libs.json.{JsFalse, JsObject, JsTrue}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Failure, Success}

@Singleton
class AnalyzerTemplateCtrl @Inject() (
    entrypoint: Entrypoint,
    @Named("with-thehive-cortex-schema") db: Database,
    properties: Properties,
    analyzerTemplateSrv: AnalyzerTemplateSrv
) extends QueryableCtrl {

  lazy val logger: Logger                                   = Logger(getClass)
  override val entityName: String                           = "analyzerTemplate"
  override val publicProperties: List[PublicProperty[_, _]] = properties.analyzerTemplate
  override val initialQuery: Query =
    Query.init[Traversal.V[AnalyzerTemplate]]("listAnalyzerTemplate", (graph, _) => analyzerTemplateSrv.startTraversal(graph))
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[AnalyzerTemplate]](
    "getReportTemplace",
    FieldsParser[IdOrName],
    (param, graph, _) => analyzerTemplateSrv.get(param.idOrName)(graph)
  )
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[AnalyzerTemplate], IteratorOutput](
      "page",
      FieldsParser[OutputParam],
      (range, analyzerTemplateTraversal, _) => analyzerTemplateTraversal.page(range.from, range.to, withTotal = true)
    )
  override val outputQuery: Query = Query.output[AnalyzerTemplate with Entity]

  def get(id: String): Action[AnyContent] =
    entrypoint("get content")
      .authTransaction(db) { _ => implicit graph =>
        analyzerTemplateSrv
          .getOrFail(id)
          .map(report => Results.Ok(report.content))
      }

  def importTemplates: Action[AnyContent] =
    entrypoint("import templates")
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
    entrypoint("create template")
      .extract("analyzerTemplate", FieldsParser[InputAnalyzerTemplate])
      .authPermittedTransaction(db, Permissions.manageAnalyzerTemplate) { implicit request => implicit graph =>
        val analyzerTemplate: InputAnalyzerTemplate = request.body("analyzerTemplate")
        analyzerTemplateSrv.create(analyzerTemplate.toAnalyzerTemplate).map { createdAnalyzerTemplate =>
          Results.Created(createdAnalyzerTemplate.toJson)
        }
      }

  def delete(id: String): Action[AnyContent] =
    entrypoint("delete template")
      .authPermittedTransaction(db, Permissions.manageAnalyzerTemplate) { implicit request => implicit graph =>
        analyzerTemplateSrv
          .get(id)
          .getOrFail("AnalyzerTemplate")
          .map { analyzerTemplate =>
            analyzerTemplateSrv.remove(analyzerTemplate)
            Results.NoContent
          }
      }

  def update(id: String): Action[AnyContent] =
    entrypoint("update template")
      .extract("template", FieldsParser.update("template", properties.analyzerTemplate))
      .authPermittedTransaction(db, Permissions.manageAnalyzerTemplate) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("template")

        for {
          (templateSteps, _) <- analyzerTemplateSrv.update(_.getByIds(id), propertyUpdaters)
          template           <- templateSteps.getOrFail("AnalyzerTemplate")
        } yield Results.Ok(template.toJson)

      }
}
