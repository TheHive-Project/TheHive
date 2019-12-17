package org.thp.thehive.controllers.v1

import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.RichOptionTry
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputAlert
import org.thp.thehive.models.{Permissions, RichAlert}
import org.thp.thehive.services._

@Singleton
class AlertCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    properties: Properties,
    alertSrv: AlertSrv,
    caseTemplateSrv: CaseTemplateSrv,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv
) extends QueryableCtrl {

  override val entityName: String                           = "alert"
  override val publicProperties: List[PublicProperty[_, _]] = properties.alert ::: metaProperties[AlertSteps]
  override val initialQuery: Query =
    Query.init[AlertSteps]("listAlert", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).alerts)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, AlertSteps](
    "getAlert",
    FieldsParser[IdOrName],
    (param, graph, authContext) => alertSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, AlertSteps, PagedResult[RichAlert]](
    "page",
    FieldsParser[OutputParam],
    (range, alertSteps, _) =>
      alertSteps
        .richPage(range.from, range.to, withTotal = true)(_.richAlert)
  )
  override val outputQuery: Query = Query.output[RichAlert]()
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[AlertSteps, List[RichAlert]]("toList", (alertSteps, _) => alertSteps.richAlert.toList)
  )

  def create: Action[AnyContent] =
    entryPoint("create alert")
      .extract("alert", FieldsParser[InputAlert])
      .extract("caseTemplate", FieldsParser[String].optional.on("caseTemplate"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String] = request.body("caseTemplate")
        val inputAlert: InputAlert           = request.body("alert")
        for {
          caseTemplate <- caseTemplateName.map { ct =>
            caseTemplateSrv
              .get(ct)
              .visible
              .getOrFail()
          }.flip
          organisation <- userSrv.current.organisations(Permissions.manageAlert).getOrFail()
          customFields = inputAlert.customFieldValue.map(cf => cf.name -> cf.value).toMap
          richAlert <- alertSrv.create(request.body("alert").toAlert, organisation, inputAlert.tags, customFields, caseTemplate)
        } yield Results.Created(richAlert.toJson)
      }

  def get(alertId: String): Action[AnyContent] =
    entryPoint("get alert")
      .authRoTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .getByIds(alertId)
          .visible
          .richAlert
          .getOrFail()
          .map(alert => Results.Ok(alert.toJson))
      }

//  def list: Action[AnyContent] =
//    entryPoint("list alert")
//      .authRoTransaction(db) { implicit request ⇒ implicit graph ⇒
//        val alerts = alertSrv.initSteps
//          .availableFor(request.organisation)
//          .richAlert
//          .map(_.toJson)
//          .toList()
//        Success(Results.Ok(Json.toJson(alerts)))
//      }

  def update(alertId: String): Action[AnyContent] =
    entryPoint("update alert")
      .extract("alert", FieldsParser.update("alertUpdate", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("alert")
        alertSrv
          .update(
            _.getByIds(alertId)
              .can(Permissions.manageAlert),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

  def mergeWithCase(alertId: String, caseId: String) = ???

  def markAsRead(alertId: String): Action[AnyContent] =
    entryPoint("mark alert as read")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .getByIds(alertId)
          .can(Permissions.manageAlert)
          .existsOrFail()
          .map { _ =>
            alertSrv.markAsRead(alertId)
            Results.NoContent
          }
      }

  def markAsUnread(alertId: String): Action[AnyContent] =
    entryPoint("mark alert as unread")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .getByIds(alertId)
          .can(Permissions.manageAlert)
          .existsOrFail()
          .map { _ =>
            alertSrv.markAsUnread(alertId)
            Results.NoContent
          }
      }

  def createCase(alertId: String): Action[AnyContent] =
    entryPoint("create case from alert")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          (alert, organisation) <- alertSrv.getByIds(alertId).alertUserOrganisation(Permissions.manageCase).getOrFail()
          richCase              <- alertSrv.createCase(alert, None, organisation)
        } yield Results.Created(richCase.toJson)
      }

  def followAlert(alertId: String): Action[AnyContent] =
    entryPoint("follow alert")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .getByIds(alertId)
          .can(Permissions.manageAlert)
          .existsOrFail()
          .map { _ =>
            alertSrv.followAlert(alertId)
            Results.NoContent
          }
      }

  def unfollowAlert(alertId: String): Action[AnyContent] =
    entryPoint("unfollow alert")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .getByIds(alertId)
          .can(Permissions.manageAlert)
          .existsOrFail()
          .map { _ =>
            alertSrv.unfollowAlert(alertId)
            Results.NoContent
          }
      }
}
