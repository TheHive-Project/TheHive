package org.thp.thehive.controllers.v1

import java.util.{Map => JMap}

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.{InputAlert, InputCustomFieldValue}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.reflect.runtime.{universe => ru}

case class SimilarCaseFilter()
@Singleton
class AlertCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    alertSrv: AlertSrv,
    caseTemplateSrv: CaseTemplateSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    implicit val db: Database
) extends QueryableCtrl
    with AlertRenderer {

  override val entityName: String                 = "alert"
  override val publicProperties: PublicProperties = properties.alert
  override val initialQuery: Query =
    Query.init[Traversal.V[Alert]]("listAlert", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).alerts)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Alert]](
    "getAlert",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, authContext) => alertSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Alert], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, alertSteps, authContext) =>
      alertSteps
        .richPage(range.from, range.to, range.extraData.contains("total"))(
          _.richAlertWithCustomRenderer(alertStatsRenderer(range.extraData)(authContext))
        )
  )
  override val outputQuery: Query      = Query.output[RichAlert, Traversal.V[Alert]](_.richAlert)
  val caseProperties: PublicProperties = properties.`case` ++ properties.metaProperties
  val caseFilterParser: FieldsParser[Option[InputQuery[Traversal.Unk, Traversal.Unk]]] =
    FilterQuery.default(caseProperties).paramParser(ru.typeOf[Traversal.V[Case]]).optional.on("caseFilter")
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Alert], Traversal.V[Observable]]("observables", (alertSteps, _) => alertSteps.observables),
    Query[Traversal.V[Alert], Traversal.V[Case]]("case", (alertSteps, _) => alertSteps.`case`),
    Query.withParam[Option[InputQuery[Traversal.Unk, Traversal.Unk]], Traversal.V[Alert], Traversal[
      JsValue,
      JMap[String, Any],
      Converter[JsValue, JMap[String, Any]]
    ]](
      "similarCases",
      caseFilterParser,
      { (maybeCaseFilterQuery, alertSteps, authContext) =>
        val maybeCaseFilter: Option[Traversal.V[Case] => Traversal.V[Case]] =
          maybeCaseFilterQuery.map(f => cases => f(caseProperties, ru.typeOf[Traversal.V[Case]], cases.cast, authContext).cast)
        alertSteps.similarCases(maybeCaseFilter)(authContext).domainMap(Json.toJson(_))
      }
    )
  )

  def create: Action[AnyContent] =
    entrypoint("create alert")
      .extract("alert", FieldsParser[InputAlert])
      .extract("caseTemplate", FieldsParser[String].optional.on("caseTemplate"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String] = request.body("caseTemplate")
        val inputAlert: InputAlert           = request.body("alert")
        val caseTemplate                     = caseTemplateName.flatMap(ct => caseTemplateSrv.get(EntityIdOrName(ct)).visible.headOption)
        for {
          organisation <- userSrv.current.organisations(Permissions.manageAlert).getOrFail("Organisation")
          customFields = inputAlert.customFieldValue.map(cf => InputCustomFieldValue(cf.name, cf.value, cf.order))
          richAlert <- alertSrv.create(inputAlert.toAlert, organisation, inputAlert.tags, customFields, caseTemplate)
        } yield Results.Created(richAlert.toJson)
      }

  def get(alertIdOrName: String): Action[AnyContent] =
    entrypoint("get alert")
      .authRoTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(EntityIdOrName(alertIdOrName))
          .visible
          .richAlert
          .getOrFail("Alert")
          .map(alert => Results.Ok(alert.toJson))
      }

  def update(alertIdOrName: String): Action[AnyContent] =
    entrypoint("update alert")
      .extract("alert", FieldsParser.update("alertUpdate", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("alert")
        alertSrv
          .update(
            _.get(EntityIdOrName(alertIdOrName))
              .can(Permissions.manageAlert),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

//  def mergeWithCase(alertId: String, caseId: String) = ???

  def markAsRead(alertIdOrName: String): Action[AnyContent] =
    entrypoint("mark alert as read")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(EntityIdOrName(alertIdOrName))
          .can(Permissions.manageAlert)
          .getOrFail("Alert")
          .map { alert =>
            alertSrv.markAsRead(alert._id)
            Results.NoContent
          }
      }

  def markAsUnread(alertIdOrName: String): Action[AnyContent] =
    entrypoint("mark alert as unread")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(EntityIdOrName(alertIdOrName))
          .can(Permissions.manageAlert)
          .getOrFail("Alert")
          .map { alert =>
            alertSrv.markAsUnread(alert._id)
            Results.NoContent
          }
      }

  def createCase(alertIdOrName: String): Action[AnyContent] =
    entrypoint("create case from alert")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          (alert, organisation) <- alertSrv.get(EntityIdOrName(alertIdOrName)).alertUserOrganisation(Permissions.manageCase).getOrFail("Alert")
          richCase              <- alertSrv.createCase(alert, None, organisation)
        } yield Results.Created(richCase.toJson)
      }

  def followAlert(alertIdOrName: String): Action[AnyContent] =
    entrypoint("follow alert")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(EntityIdOrName(alertIdOrName))
          .can(Permissions.manageAlert)
          .getOrFail("Alert")
          .map { alert =>
            alertSrv.followAlert(alert._id)
            Results.NoContent
          }
      }

  def unfollowAlert(alertIdOrName: String): Action[AnyContent] =
    entrypoint("unfollow alert")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(EntityIdOrName(alertIdOrName))
          .can(Permissions.manageAlert)
          .getOrFail("Alert")
          .map { alert =>
            alertSrv.unfollowAlert(alert._id)
            Results.NoContent
          }
      }
}
