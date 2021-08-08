package org.thp.thehive.controllers.v1

import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{Converter, IteratorOutput, Traversal}
import org.thp.scalligraph.{EntityIdOrName, RichOptionTry}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.{InputAlert, InputCustomFieldValue, InputShare}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import java.util.{Map => JMap}
import scala.reflect.runtime.{universe => ru}
import scala.util.Success

case class SimilarCaseFilter()

class AlertCtrl(
    entrypoint: Entrypoint,
    properties: Properties,
    alertSrv: AlertSrv,
    caseTemplateSrv: CaseTemplateSrv,
    userSrv: UserSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val customFieldValueSrv: CustomFieldValueSrv,
    implicit val db: Database
) extends QueryableCtrl
    with AlertRenderer {

  override val entityName: String                 = "alert"
  override val publicProperties: PublicProperties = properties.alert
  override val initialQuery: Query =
    Query.init[Traversal.V[Alert]]("listAlert", (graph, authContext) => alertSrv.startTraversal(graph).visible(authContext))

  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Alert]](
    "getAlert",
    (idOrName, graph, authContext) => alertSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Alert], IteratorOutput](
    "page",
    (range, alertSteps, authContext) =>
      alertSteps
        .richPage(range.from, range.to, range.extraData.contains("total"))(
          _.richAlertWithCustomRenderer(alertStatsRenderer(organisationSrv, range.extraData)(authContext))
        )
  )
  override val outputQuery: Query      = Query.output[RichAlert, Traversal.V[Alert]](_.richAlert)
  val caseProperties: PublicProperties = properties.`case` ++ properties.metaProperties
  implicit val caseFilterParser: FieldsParser[Option[InputFilter]] =
    FilterQuery.default(caseProperties).paramParser(ru.typeOf[Traversal.V[Case]]).optional.on("caseFilter")
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query.init[Long](
      "countAlert",
      (graph, authContext) =>
        graph.indexCountQuery(s"""v."_label":Alert AND v.organisationId:${organisationSrv.currentId(graph, authContext).value}""")
    ),
    Query.init[Long](
      "countUnreadAlert",
      (graph, authContext) =>
        graph.indexCountQuery(s"""v."_label":Alert AND v.organisationId:${organisationSrv.currentId(graph, authContext).value} AND v.read:false""")
    ),
    Query.init[Long](
      "countImportedAlert",
      (graph, authContext) =>
        graph.indexCountQuery(
          s"""v."_label":Alert AND v.organisationId:${organisationSrv.currentId(graph, authContext).value} AND NOT v.caseId:[* TO 'ZZZZZZZZ']"""
        )
    ),
    Query.initWithParam[InCase, Long](
      "countRelatedAlert",
      (inCase, graph, authContext) =>
        graph.indexCountQuery(
          s"""v."_label":Alert AND """ +
            s"v.organisationId:${organisationSrv.currentId(graph, authContext).value} AND " +
            s"v.caseId:${graph.escapeQueryParameter(inCase.caseId.value)}"
        )
    ),
    Query[Traversal.V[Alert], Traversal.V[Observable]]("observables", (alertSteps, _) => alertSteps.observables),
    Query[Traversal.V[Alert], Traversal.V[Case]]("case", (alertSteps, _) => alertSteps.`case`),
    Query.withParam[Option[InputFilter], Traversal.V[Alert], Traversal[
      JsValue,
      JMap[String, Any],
      Converter[JsValue, JMap[String, Any]]
    ]](
      "similarCases",
      { (maybeCaseFilterQuery, alertSteps, authContext) =>
        val caseFilter: Option[Traversal.V[Case] => Traversal.V[Case]] =
          maybeCaseFilterQuery.map(f => cases => f(caseProperties, ru.typeOf[Traversal.V[Case]], cases.cast, authContext).cast)
        alertSteps.similarCases(caseFilter)(authContext).domainMap(Json.toJson(_))
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
          richAlert <- alertSrv.create(inputAlert.toAlert, organisation, inputAlert.tags.map(_.value), customFields, caseTemplate)
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
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("alert")
        alertSrv
          .update(
            _.get(EntityIdOrName(alertIdOrName)).visible,
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

//  def mergeWithCase(alertId: String, caseId: String) = ???

  def markAsRead(alertIdOrName: String): Action[AnyContent] =
    entrypoint("mark alert as read")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        alertSrv
          .get(EntityIdOrName(alertIdOrName))
          .visible
          .getOrFail("Alert")
          .map { alert =>
            alertSrv.markAsRead(alert._id)
            Results.NoContent
          }
      }

  def markAsUnread(alertIdOrName: String): Action[AnyContent] =
    entrypoint("mark alert as unread")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        alertSrv
          .get(EntityIdOrName(alertIdOrName))
          .visible
          .getOrFail("Alert")
          .map { alert =>
            alertSrv.markAsUnread(alert._id)
            Results.NoContent
          }
      }

  def createCase(alertIdOrName: String): Action[AnyContent] =
    entrypoint("create case from alert")
      .extract("caseTemplate", FieldsParser.string.optional.on("caseTemplate"))
      .extract("sharingParameters", FieldsParser[InputShare].sequence.on("sharingParameters"))
      .extract("taskRule", FieldsParser[String].optional.on("taskRule"))
      .extract("observableRule", FieldsParser[String].optional.on("observableRule"))
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        val caseTemplate: Option[String]       = request.body("caseTemplate")
        val sharingParameters: Seq[InputShare] = request.body("sharingParameters")
        val taskRule: Option[String]           = request.body("taskRule")
        val observableRule: Option[String]     = request.body("observableRule")

        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertIdOrName))
              .visible
              .richAlert
              .getOrFail("Alert")
          _ <- caseTemplate.map(ct => caseTemplateSrv.get(EntityIdOrName(ct)).visible.existsOrFail).flip
          alertWithCaseTemplate = caseTemplate.fold(alert)(ct => alert.copy(caseTemplate = Some(ct)))
          assignee <- if (request.isPermitted(Permissions.manageCase)) userSrv.current.getOrFail("User").map(Some(_)) else Success(None)
          richCase <- alertSrv.createCase(
            alertWithCaseTemplate,
            assignee,
            sharingParameters.map(_.toSharingParameter).toMap,
            taskRule,
            observableRule
          )
        } yield Results.Created(richCase.toJson)
      }

  def followAlert(alertIdOrName: String): Action[AnyContent] =
    entrypoint("follow alert")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        alertSrv
          .get(EntityIdOrName(alertIdOrName))
          .visible
          .getOrFail("Alert")
          .map { alert =>
            alertSrv.followAlert(alert._id)
            Results.NoContent
          }
      }

  def unfollowAlert(alertIdOrName: String): Action[AnyContent] =
    entrypoint("unfollow alert")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        alertSrv
          .get(EntityIdOrName(alertIdOrName))
          .visible
          .getOrFail("Alert")
          .map { alert =>
            alertSrv.unfollowAlert(alert._id)
            Results.NoContent
          }
      }
}
