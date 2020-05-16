package org.thp.thehive.controllers.v0

import java.util.Base64

import gremlin.scala.{__, By, Graph, Key, StepLabel, Vertex}
import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{Entrypoint, FString, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{PagedResult, Traversal}
import org.thp.scalligraph.{AuthorizationError, InvalidFormatAttributeError, RichJMap, RichSeq}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputAlert, InputObservable, OutputSimilarCase}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

@Singleton
class AlertCtrl @Inject() (
    entrypoint: Entrypoint,
    db: Database,
    properties: Properties,
    alertSrv: AlertSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    attachmentSrv: AttachmentSrv,
    organisationSrv: OrganisationSrv,
    auditSrv: AuditSrv,
    val userSrv: UserSrv,
    val caseSrv: CaseSrv
) extends QueryableCtrl {
  lazy val logger: Logger                                   = Logger(getClass)
  override val entityName: String                           = "alert"
  override val publicProperties: List[PublicProperty[_, _]] = properties.alert ::: metaProperties[AlertSteps]
  override val initialQuery: Query =
    Query.init[AlertSteps]("listAlert", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).alerts)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, AlertSteps](
    "getAlert",
    FieldsParser[IdOrName],
    (param, graph, authContext) => alertSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, AlertSteps, PagedResult[(RichAlert, Seq[RichObservable])]](
    "page",
    FieldsParser[OutputParam],
    (range, alertSteps, _) =>
      alertSteps
        .richPage(range.from, range.to, withTotal = true)(_.richAlert)
        .map { richAlert =>
          richAlert -> alertSrv.get(richAlert.alert)(alertSteps.graph).observables.richObservable.toList
        }
  )
  override val outputQuery: Query = Query.output[RichAlert, AlertSteps](_.richAlert)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[AlertSteps, CaseSteps]("cases", (alertSteps, _) => alertSteps.`case`),
    Query[AlertSteps, ObservableSteps]("observables", (alertSteps, _) => alertSteps.observables),
    Query[AlertSteps, Traversal[(RichAlert, Seq[RichObservable]), (RichAlert, Seq[RichObservable])]](
      "withObservables",
      (alertSteps, _) =>
        alertSteps
          .richAlert
          .map { richAlert =>
            richAlert -> alertSrv.get(richAlert.alert)(alertSteps.graph).observables.richObservable.toList
          }
    ),
    Query.output[(RichAlert, Seq[RichObservable])]
  )

  def create: Action[AnyContent] =
    entrypoint("create alert")
      .extract("alert", FieldsParser[InputAlert])
      .extract("caseTemplate", FieldsParser[String].optional.on("caseTemplate"))
      .extract("observables", FieldsParser[InputObservable].sequence.on("artifacts"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String]  = request.body("caseTemplate")
        val inputAlert: InputAlert            = request.body("alert")
        val observables: Seq[InputObservable] = request.body("observables")
        val customFields                      = inputAlert.customFields.map(c => c.name -> c.value).toMap
        val caseTemplate                      = caseTemplateName.flatMap(caseTemplateSrv.get(_).visible.headOption())
        for {
          organisation <- userSrv
            .current
            .organisations(Permissions.manageAlert)
            .get(request.organisation)
            .orFail(AuthorizationError("Operation not permitted"))
          richObservables <- observables.toTry(createObservable).map(_.flatten)
          richAlert       <- alertSrv.create(request.body("alert").toAlert, organisation, inputAlert.tags, customFields, caseTemplate)
          _               <- auditSrv.mergeAudits(richObservables.toTry(o => alertSrv.addObservable(richAlert.alert, o)))(_ => Success(()))
        } yield Results.Created((richAlert -> richObservables).toJson)
      }

  def alertSimilarityRenderer(implicit authContext: AuthContext, db: Database, graph: Graph): AlertSteps => Traversal[JsArray, JsArray] = {
    alertSteps =>
      val observableLabel = StepLabel[Vertex]()
      val caseLabel       = StepLabel[Vertex]()
      Traversal(
        alertSteps
          .observables
          .similar
          .as(observableLabel)
          .`case`
          .as(caseLabel)
          .raw
          .select(observableLabel.name, caseLabel.name)
          .fold
          .map { resultMapList =>
            val similarCases = resultMapList
              .asScala
              .map { m =>
                val cid = m.getValue(caseLabel).id()
                val ioc = m.getValue(observableLabel).value[Boolean]("ioc")
                cid -> ioc
              }
              .groupBy(_._1)
              .map {
                case (cid, cidIoc) =>
                  val iocStats = cidIoc.groupBy(_._2).mapValues(_.size)
                  val (caseVertex, observableCount, resolutionStatus) = caseSrv
                    .getByIds(cid.toString)
                    .project(
                      _(By[Vertex]())
                        .and(By(new CaseSteps(__[Vertex]).observables(authContext).groupCount(By(Key[Boolean]("ioc"))).raw))
                        .and(By(__[Vertex].outTo[CaseResolutionStatus].values[String]("value").fold))
                    )
                    .head()
                  val case0 = caseVertex
                    .as[Case]
                  val similarCase = case0
                    .asInstanceOf[Case]
                    .into[OutputSimilarCase]
                    .withFieldConst(_.artifactCount, observableCount.getOrDefault(false, 0L).toInt)
                    .withFieldConst(_.iocCount, observableCount.getOrDefault(true, 0L).toInt)
                    .withFieldConst(_.similarArtifactCount, iocStats.getOrElse(false, 0))
                    .withFieldConst(_.similarIOCCount, iocStats.getOrElse(true, 0))
                    .withFieldConst(_.resolutionStatus, atMostOneOf[String](resolutionStatus))
                    .withFieldComputed(_.status, _.status.toString)
                    .withFieldConst(_.id, case0._id)
                    .withFieldConst(_._id, case0._id)
                    .withFieldRenamed(_.number, _.caseId)
                    .transform
                  Json.toJson(similarCase)
              }
            JsArray(similarCases.toSeq)
          }
      )
  }

  def get(alertId: String): Action[AnyContent] =
    entrypoint("get alert")
      .extract("similarity", FieldsParser[Boolean].optional.on("similarity"))
      .authRoTransaction(db) { implicit request => implicit graph =>
        val similarity: Option[Boolean] = request.body("similarity")
        val alert =
          alertSrv
            .get(alertId)
            .visible
        if (similarity.contains(true))
          alert
            .richAlertWithCustomRenderer(alertSimilarityRenderer(request, db, graph))
            .getOrFail()
            .map {
              case (richAlert, similarCases) =>
                val alertWithObservables: (RichAlert, Seq[RichObservable]) =
                  richAlert -> alertSrv.get(richAlert.alert).observables.richObservableWithSeen.toList

                Results.Ok(alertWithObservables.toJson.as[JsObject] + ("similarCases" -> similarCases))
            }
        else
          alert
            .richAlert
            .getOrFail()
            .map { richAlert =>
              val alertWithObservables: (RichAlert, Seq[RichObservable]) =
                richAlert -> alertSrv.get(richAlert.alert).observables.richObservable.toList
              Results.Ok(alertWithObservables.toJson)
            }

      }

  def update(alertId: String): Action[AnyContent] =
    entrypoint("update alert")
      .extract("alert", FieldsParser.update("alert", publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("alert")
        alertSrv
          .update(_.get(alertId).can(Permissions.manageAlert), propertyUpdaters)
          .flatMap { case (alertSteps, _) => alertSteps.richAlert.getOrFail() }
          .map { richAlert =>
            val alertWithObservables: (RichAlert, Seq[RichObservable]) = richAlert -> alertSrv.get(richAlert.alert).observables.richObservable.toList
            Results.Ok(alertWithObservables.toJson)
          }
      }

  def delete(alertId: String): Action[AnyContent] =
    entrypoint("delete alert")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          alert <- alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .getOrFail()
          _ <- alertSrv.cascadeRemove(alert)
        } yield Results.NoContent
      }

  def mergeWithCase(alertId: String, caseId: String): Action[AnyContent] =
    entrypoint("merge alert with case")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          alert    <- alertSrv.get(alertId).can(Permissions.manageAlert).getOrFail()
          case0    <- caseSrv.get(caseId).can(Permissions.manageCase).getOrFail()
          _        <- alertSrv.mergeInCase(alert, case0)
          richCase <- caseSrv.get(caseId).richCase.getOrFail()
        } yield Results.Ok(richCase.toJson)
      }

  def bulkMergeWithCase: Action[AnyContent] =
    entrypoint("bulk merge with case")
      .extract("caseId", FieldsParser.string.on("caseId"))
      .extract("alertIds", FieldsParser.string.sequence.on("alertIds"))
      .authTransaction(db) { implicit request => implicit graph =>
        val alertIds: Seq[String] = request.body("alertIds")
        val caseId: String        = request.body("caseId")
        for {
          case0 <- caseSrv.get(caseId).can(Permissions.manageCase).getOrFail()
          _ <- alertIds.toTry { alertId =>
            alertSrv
              .get(alertId)
              .can(Permissions.manageAlert)
              .getOrFail()
              .flatMap(alertSrv.mergeInCase(_, case0))
          }
          richCase <- caseSrv.get(caseId).richCase.getOrFail()
        } yield Results.Ok(richCase.toJson)
      }

  def markAsRead(alertId: String): Action[AnyContent] =
    entrypoint("mark alert as read")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(alertId)
          .can(Permissions.manageAlert)
          .existsOrFail()
          .map { _ =>
            alertSrv.markAsRead(alertId)
            Results.NoContent
          }
      }

  def markAsUnread(alertId: String): Action[AnyContent] =
    entrypoint("mark alert as unread")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(alertId)
          .can(Permissions.manageAlert)
          .existsOrFail()
          .map { _ =>
            alertSrv.markAsUnread(alertId)
            Results.NoContent
          }
      }

  def createCase(alertId: String): Action[AnyContent] =
    entrypoint("create case from alert")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          (alert, organisation) <- alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .alertUserOrganisation(Permissions.manageCase)
            .getOrFail()
          richCase <- alertSrv.createCase(alert, None, organisation)
        } yield Results.Created(richCase.toJson)
      }

  def followAlert(alertId: String): Action[AnyContent] =
    entrypoint("follow alert")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(alertId)
          .can(Permissions.manageAlert)
          .existsOrFail()
          .map { _ =>
            alertSrv.followAlert(alertId)
            Results.NoContent
          }
      }

  def unfollowAlert(alertId: String): Action[AnyContent] =
    entrypoint("unfollow alert")
      .authTransaction(db) { implicit request => implicit graph =>
        alertSrv
          .get(alertId)
          .can(Permissions.manageAlert)
          .existsOrFail()
          .map { _ =>
            alertSrv.unfollowAlert(alertId)
            Results.NoContent
          }
      }

  private def createObservable(observable: InputObservable)(
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
                .flatMap(attachment => observableSrv.create(observable.toObservable, attachmentType, attachment, observable.tags, Nil))
            case data =>
              Failure(InvalidFormatAttributeError("artifacts.data", "filename;contentType;base64value", Set.empty, FString(data.mkString(";"))))
          }
        case dataType => observable.data.toTry(d => observableSrv.create(observable.toObservable, dataType, d, observable.tags, Nil))
      }
}
