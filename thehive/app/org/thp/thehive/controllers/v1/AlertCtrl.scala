package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputAlert
import org.thp.thehive.models.{Permissions, RichAlert}
import org.thp.thehive.services._
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class AlertCtrl @Inject() (
    entrypoint: Entrypoint,
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
  override val outputQuery: Query               = Query.output[RichAlert, AlertSteps](_.richAlert)
  override val extraQueries: Seq[ParamQuery[_]] = Seq()

  def create: Action[AnyContent] =
    entrypoint("create alert")
      .extract("alert", FieldsParser[InputAlert])
      .extract("caseTemplate", FieldsParser[String].optional.on("caseTemplate"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String] = request.body("caseTemplate")
        val inputAlert: InputAlert           = request.body("alert")
        val caseTemplate                     = caseTemplateName.flatMap(ct => caseTemplateSrv.get(ct).visible.headOption())
        for {
          organisation <- userSrv.current.organisations(Permissions.manageAlert).getOrFail("Organisation")
          customFields = inputAlert.customFieldValue.map(cf => cf.name -> cf.value).toMap
          richAlert <- alertSrv.create(request.body("alert").toAlert, organisation, inputAlert.tags, customFields, caseTemplate)
        } yield Results.Created(richAlert.toJson)
      }

  def get(alertId: String): Action[AnyContent] =
    entrypoint("get alert")
      .authRoTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(alertId)
          .visible
          .richAlert
          .getOrFail("Alert")
          .map(alert => Results.Ok(alert.toJson))
      }

  def update(alertId: String): Action[AnyContent] =
    entrypoint("update alert")
      .extract("alert", FieldsParser.update("alertUpdate", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("alert")
        alertSrv
          .update(
            _.get(alertId)
              .can(Permissions.manageAlert),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }

//  def mergeWithCase(alertId: String, caseId: String) = ???

  def markAsRead(alertId: String): Action[AnyContent] =
    entrypoint("mark alert as read")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(alertId)
          .can(Permissions.manageAlert)
          .getOrFail()
          .map { alert =>
            alertSrv.markAsRead(alert._id)
            Results.NoContent
          }
      }

  def markAsUnread(alertId: String): Action[AnyContent] =
    entrypoint("mark alert as unread")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(alertId)
          .can(Permissions.manageAlert)
          .getOrFail()
          .map { alert =>
            alertSrv.markAsUnread(alert._id)
            Results.NoContent
          }
      }

  def createCase(alertId: String): Action[AnyContent] =
    entrypoint("create case from alert")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          (alert, organisation) <- alertSrv.get(alertId).alertUserOrganisation(Permissions.manageCase).getOrFail("Alert")
          richCase              <- alertSrv.createCase(alert, None, organisation)
        } yield Results.Created(richCase.toJson)
      }

  def followAlert(alertId: String): Action[AnyContent] =
    entrypoint("follow alert")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(alertId)
          .can(Permissions.manageAlert)
          .getOrFail()
          .map { alert =>
            alertSrv.followAlert(alert._id)
            Results.NoContent
          }
      }

  def unfollowAlert(alertId: String): Action[AnyContent] =
    entrypoint("unfollow alert")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(alertId)
          .can(Permissions.manageAlert)
          .getOrFail()
          .map { alert =>
            alertSrv.unfollowAlert(alert._id)
            Results.NoContent
          }
      }
}
