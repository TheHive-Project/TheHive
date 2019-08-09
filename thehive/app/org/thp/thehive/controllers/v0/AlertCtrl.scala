package org.thp.thehive.controllers.v0

import java.util.Base64

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph._
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{EntryPoint, FString, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.thehive.dto.v0.{InputAlert, InputObservable, OutputAlert}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Failure, Success, Try}

@Singleton
class AlertCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    alertSrv: AlertSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    attachmentSrv: AttachmentSrv,
    organisationSrv: OrganisationSrv,
    val userSrv: UserSrv,
    val caseSrv: CaseSrv
) extends QueryableCtrl {
  import AlertConversion._
  import CaseConversion._
  import CustomFieldConversion._
  import ObservableConversion._

  lazy val logger                                           = Logger(getClass)
  override val entityName: String                           = "alert"
  override val publicProperties: List[PublicProperty[_, _]] = alertProperties(alertSrv) ::: metaProperties[AlertSteps]
  override val initialQuery: Query =
    Query.init[AlertSteps]("listAlert", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).alerts)
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, AlertSteps, PagedResult[(RichAlert, Seq[RichObservable])]](
    "page",
    FieldsParser[OutputParam],
    (range, alertSteps, _) =>
      alertSteps
        .richPage(range.from, range.to, true)(_.richAlert.raw)
        .map { richAlert =>
          richAlert -> alertSrv.get(richAlert.alert)(alertSteps.graph).observables.richObservable.toList
        }
  )
  override val outputQuery: Query = Query.output[RichAlert, OutputAlert]

  def create: Action[AnyContent] =
    entryPoint("create alert")
      .extract("alert", FieldsParser[InputAlert])
      .extract("caseTemplate", FieldsParser[String].optional.on("caseTemplate"))
      .extract("observables", FieldsParser[InputObservable].sequence.on("artifacts"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String]  = request.body("caseTemplate")
        val inputAlert: InputAlert            = request.body("alert")
        val observables: Seq[InputObservable] = request.body("observables")
        for {
          caseTemplate <- caseTemplateName.fold[Try[Option[RichCaseTemplate]]](Success(None)) { ct =>
            caseTemplateSrv
              .get(ct)
              .visible
              .richCaseTemplate
              .getOrFail()
              .map(Some(_))
          }

          user         <- userSrv.getOrFail(request.userId)
          organisation <- userSrv.getOrganisation(user)
          customFields = inputAlert.customFieldValue.map(fromInputCustomField).toMap
          _               <- userSrv.current.can(Permissions.manageAlert).existsOrFail()
          richAlert       <- alertSrv.create(request.body("alert"), organisation, inputAlert.tags, customFields, caseTemplate)
          richObservables <- observables.toTry(observable => importObservable(richAlert.alert, observable))
        } yield Results.Created((richAlert -> richObservables.flatten).toJson)
      }

  private def importObservable(alert: Alert with Entity, observable: InputObservable)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Seq[RichObservable]] =
    observableTypeSrv
      .getOrFail(observable.dataType)
      .flatMap {
        case attachmentType if attachmentType.isAttachment =>
          observable.data.map(_.split(';')).toTry {
            case Array(filename, contentType, value) =>
              val data = Base64.getDecoder.decode(value)
              attachmentSrv
                .create(filename, contentType, data)
                .flatMap(attachment => observableSrv.create(observable, attachmentType, attachment, observable.tags, Nil))
            case data =>
              Failure(InvalidFormatAttributeError("artifacts.data", "filename;contentType;base64value", Set.empty, FString(data.mkString(";"))))
          }
        case dataType => observable.data.toTry(d => observableSrv.create(observable, dataType, d, observable.tags, Nil))
      }
      .map(_.map { richObservable =>
        alertSrv.addObservable(alert, richObservable.observable)
        richObservable
      })

  def get(alertId: String): Action[AnyContent] =
    entryPoint("get alert")
      .authRoTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .getByIds(alertId)
          .visible
          .richAlert
          .getOrFail()
          .map { richAlert =>
            Results.Ok((richAlert -> alertSrv.get(richAlert.alert).observables.richObservable.toList).toJson)
          }
      }

  def update(alertId: String): Action[AnyContent] =
    entryPoint("update alert")
      .extract("alert", FieldsParser.update("alert", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("alert")
        alertSrv
          .update(_.getByIds(alertId).can(Permissions.manageAlert), propertyUpdaters)
          .flatMap { case (alertSteps, _) => alertSteps.richAlert.getOrFail() }
          .map { richAlert =>
            Results.Ok((richAlert -> alertSrv.get(richAlert.alert).observables.richObservable.toList).toJson)
          }
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
          (alert, organisation) <- alertSrv
            .getByIds(alertId)
            .visible
            .alertUserOrganisation(Permissions.manageCase)
            .getOrFail()
          richCase <- alertSrv.createCase(alert, None, organisation)
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
