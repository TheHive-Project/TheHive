package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.{EntityIdOrName, NotFoundError}
import org.thp.scalligraph.controllers.{Entrypoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, IndexType, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal, TraversalOps}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.dto.v0.InputAnalyzerTemplate
import org.thp.thehive.connector.cortex.models.AnalyzerTemplate
import org.thp.thehive.connector.cortex.services.{AnalyzerTemplateSrv, CortexOps}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.controllers.v0.{OutputParam, PublicData, QueryCtrl}
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{SearchSrv, TheHiveOpsNoDeps}
import play.api.libs.json.{JsFalse, JsObject, JsTrue}
import play.api.mvc.{Action, AnyContent, Results}

import java.util.zip.ZipFile
import scala.util.{Failure, Success}

class AnalyzerTemplateCtrl(
    override val entrypoint: Entrypoint,
    override val db: Database,
    analyzerTemplateSrv: AnalyzerTemplateSrv,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicAnalyzerTemplate
) extends QueryCtrl
    with CortexOps
    with TheHiveOpsNoDeps {

  def get(id: String): Action[AnyContent] =
    entrypoint("get content")
      .authTransaction(db) { _ => implicit graph =>
        analyzerTemplateSrv
          .getOrFail(EntityIdOrName(id))
          .map(report => Results.Ok(report.content))
          .recover {
            case _: NotFoundError => Results.NotFound(s"AnalyzerTemplate $id not found")
          }
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
          .get(EntityIdOrName(id))
          .getOrFail("AnalyzerTemplate")
          .map { analyzerTemplate =>
            analyzerTemplateSrv.remove(analyzerTemplate)
            Results.NoContent
          }
      }

  def update(id: String): Action[AnyContent] =
    entrypoint("update template")
      .extract("template", FieldsParser.update("template", publicData.publicProperties))
      .authPermittedTransaction(db, Permissions.manageAnalyzerTemplate) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("template")

        for {
          (templateSteps, _) <- analyzerTemplateSrv.update(_.get(EntityIdOrName(id)), propertyUpdaters)
          template           <- templateSteps.getOrFail("AnalyzerTemplate")
        } yield Results.Ok(template.toJson)

      }
}

class PublicAnalyzerTemplate(analyzerTemplateSrv: AnalyzerTemplateSrv, searchSrv: SearchSrv) extends PublicData with TraversalOps {
  override val entityName: String = "analyzerTemplate"
  override val initialQuery: Query =
    Query.init[Traversal.V[AnalyzerTemplate]]("listAnalyzerTemplate", (graph, _) => analyzerTemplateSrv.startTraversal(graph))
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[AnalyzerTemplate]](
    "getReportTemplate",
    (idOrName, graph, _) => analyzerTemplateSrv.get(idOrName)(graph)
  )
  override def pageQuery(limitedCountThreshold: Long): ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[AnalyzerTemplate], IteratorOutput](
      "page",
      (range, analyzerTemplateTraversal, _) => analyzerTemplateTraversal.page(range.from, range.to, withTotal = true, limitedCountThreshold)
    )
  override val outputQuery: Query = Query.output[AnalyzerTemplate with Entity]
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[AnalyzerTemplate]
    .property("keyword", UMapping.string)(
      _.select(_.empty.asInstanceOf[Traversal[String, _, _]])
        .filter[String](IndexType.fulltext) {
          case (_, t, _, Right(p))   => searchSrv("AnalyzerTemplate", p.getValue)(t)
          case (_, t, _, Left(true)) => t
          case (_, t, _, _)          => t.empty
        }
        .readonly
    )
    .property("analyzerId", UMapping.string)(_.rename("workerId").readonly)
    .property("content", UMapping.string)(_.field.updatable)
    .build
}
