package org.thp.thehive.controllers.v0

import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.libs.json.{JsNull, JsValue}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

class SearchCtrl(
    entrypoint: Entrypoint,
    db: Database,
    searchSrv: SearchSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv
) extends TheHiveOps {

  def search: Action[AnyContent] =
    entrypoint("search")
      .extract("query", FieldsParser[String].on("query"))
      .extract("from", FieldsParser[Int].optional.on("from"))
      .extract("to", FieldsParser[Int].optional.on("to"))
      .auth { implicit request =>
        val query: String = request.body("query")
        val from: Int     = request.body("from").getOrElse(0)
        val to: Int       = request.body("to").getOrElse(10)
        val results = db.source[JsValue] { implicit graph =>
          searchSrv(query)(graph.VV())
            .chooseValue(
              _.on(_.label)
                .option("Alert", _.v[Alert].coalesce(_.visible.richAlert.domainMap(_.toJson), JsNull))
                .option("Audit", _.v[Audit].coalesce(_.visible.richAudit.domainMap(_.toJson), JsNull))
                .option("Case", _.v[Case].coalesce(_.visible.richCase.domainMap(_.toJson), JsNull))
                .option("CaseTemplate", _.v[CaseTemplate].coalesce(_.visible.richCaseTemplate.domainMap(_.toJson), JsNull))
                .option("CustomField", _.v[CustomField].domainMap(_.toJson))
//            .option("Dashboard", _.v[Dashboard].domainMap(_.toJson))
                .option("Log", _.v[Log].coalesce(_.visible.richLog.domainMap(_.toJson), JsNull))
                .option("Observable", _.v[Observable].coalesce(_.visible.richObservable.domainMap(_.toJson), JsNull))
                .option("ObservableType", _.v[ObservableType].domainMap(_.toJson))
                .option("Organisation", _.v[Organisation].coalesce(_.visible.richOrganisation.domainMap(_.toJson), JsNull))
//            //      pageCtrl,
//                  .option("Pattern", _.v[Pattern].richPattern.domainMap(_.toJson))
//                  .option("Procedure", _.v[Procedure].coalesce(_.visible.richProcedure.domainMap(_.toJson), JsNull))
                .option("Profile", _.v[Profile].domainMap(_.toJson))
                .option("Share", _.v[Share].coalesce(_.visible.richShare.domainMap(_.toJson), JsNull))
                .option("Tag", _.v[Tag].coalesce(_.visible.domainMap(_.toJson), JsNull))
                .option("Task", _.v[Task].coalesce(_.visible.richTask.domainMap(_.toJson), JsNull))
//                  .option("Taxonomy", _.v[Taxonomy].coalesce(_.visible.richTaxonomy.domainMap(_.toJson), JsNull))
                .option("User", _.v[User].coalesce(_.visible.richUser.domainMap(_.toJson), JsNull))
            )
            .toIterator
            .filterNot(_ == JsNull)
            .slice(from, to)
        }
        Success(Results.Ok.chunked(results.map(_.toString).intersperse("[", ",", "]"), Some("application/json")))
      }
}
