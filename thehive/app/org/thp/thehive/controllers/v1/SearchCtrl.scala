package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.controllers.Entrypoint
import org.thp.scalligraph.models._
import org.thp.scalligraph.traversal.{IdentityConverter, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.{CustomFieldSrv, OrganisationSrv, TheHiveOps}
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

class SearchCtrl(
    entrypoint: Entrypoint,
    schema: Schema,
    db: Database,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv
) extends TheHiveOps {

  private def isStringField(fieldName: String): Boolean =
    schema.modelList.exists(_.fields.get(fieldName).exists(_.graphTypeClass == classOf[String]))

  lazy val (stdFields, fullTextFields, fullTextOnlyFields) = schema
    .modelList
    .flatMap(_.indexes)
    .foldLeft((Set.empty[String], Set.empty[String], Set.empty[String])) {
      case ((std, ft, fto), (IndexType.standard, f))     => (std ++ f.filter(isStringField), ft, fto)
      case ((std, ft, fto), (IndexType.fulltext, f))     => (std, ft ++ f.filter(isStringField), fto)
      case ((std, ft, fto), (IndexType.fulltextOnly, f)) => (std, ft, fto ++ f.filter(isStringField))
      case (i, _)                                        => i
    }

  def search(query: String): Action[AnyContent] =
    entrypoint("search").auth { implicit request =>
      val results = db.source[JsValue] { implicit graph =>
        val filters = stdFields
          .toSeq
          .flatMap(f =>
            Seq(
              (_: Traversal[Vertex, Vertex, IdentityConverter[Vertex]]).unsafeHas(f, TextPredicate.startsWith(query)),
              (_: Traversal[Vertex, Vertex, IdentityConverter[Vertex]]).unsafeHas(f, P.eq(query))
            )
          ) ++
          fullTextFields
            .toSeq
            .flatMap(f =>
              Seq(
                (_: Traversal[Vertex, Vertex, IdentityConverter[Vertex]]).unsafeHas(f, FullTextPredicate.contains(query)),
                (_: Traversal[Vertex, Vertex, IdentityConverter[Vertex]]).unsafeHas(f, TextPredicate.startsWith(query)),
                (_: Traversal[Vertex, Vertex, IdentityConverter[Vertex]]).unsafeHas(f, P.eq(query))
              )
            ) ++
          fullTextOnlyFields.toSeq.map(f => (_: Traversal[Vertex, Vertex, IdentityConverter[Vertex]]).unsafeHas(f, FullTextPredicate.contains(query)))

        if (filters.isEmpty) Iterator.empty
        else
          graph
            .VV()
            .or(filters: _*)
            .chooseValue(
              _.on(_.label)
                .option("Alert", _.v[Alert].visible.richAlert.domainMap(_.toJson))
                .option("Audit", _.v[Audit].visible.richAudit.domainMap(_.toJson))
                .option("Case", _.v[Case].visible.richCase.domainMap(_.toJson))
                .option("CaseTemplate", _.v[CaseTemplate].visible.richCaseTemplate.domainMap(_.toJson))
                .option("CustomField", _.v[CustomField].domainMap(_.toJson))
//            .option("Dashboard", _.v[Dashboard].domainMap(_.toJson))
                .option("Log", _.v[Log].visible.richLog.domainMap(_.toJson))
                .option("Observable", _.v[Observable].visible.richObservable.domainMap(_.toJson))
                .option("ObservableType", _.v[ObservableType].domainMap(_.toJson))
                .option("Organisation", _.v[Organisation].visible.richOrganisation.domainMap(_.toJson))
//            //      pageCtrl,
                .option("Pattern", _.v[Pattern].richPattern.domainMap(_.toJson))
                .option("Procedure", _.v[Procedure].visible.richProcedure.domainMap(_.toJson))
                .option("Profile", _.v[Profile].domainMap(_.toJson))
                .option("Share", _.v[Share].visible.richShare.domainMap(_.toJson))
                .option("Tag", _.v[Tag].visible.domainMap(_.toJson))
                .option("Task", _.v[Task].visible.richTask.domainMap(_.toJson))
                .option("Taxonomy", _.v[Taxonomy].visible.richTaxonomy.domainMap(_.toJson))
                .option("User", _.v[User].visible.richUser.domainMap(_.toJson))
            )
            .toIterator
      }
      Success(Results.Ok.chunked(results.map(_.toString).intersperse("[", ",", "]"), Some("application/json")))
    }
}
