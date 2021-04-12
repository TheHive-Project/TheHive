package org.thp.thehive.controllers.v0

import io.scalaland.chimney.dsl._
import org.apache.tinkerpop.gremlin.process.traversal.{Compare, Contains}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, Entity, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal._
import org.thp.scalligraph.{
  AuthorizationError,
  BadRequestError,
  EntityId,
  EntityIdOrName,
  EntityName,
  InvalidFormatAttributeError,
  RichOptionTry,
  RichSeq
}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputAlert, InputObservable, OutputSimilarCase}
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import java.util.function.BiPredicate
import java.util.{Base64, List => JList, Map => JMap}
import javax.inject.{Inject, Named, Singleton}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

@Singleton
class AlertCtrl @Inject() (
    override val entrypoint: Entrypoint,
    alertSrv: AlertSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableTypeSrv: ObservableTypeSrv,
    attachmentSrv: AttachmentSrv,
    auditSrv: AuditSrv,
    userSrv: UserSrv,
    caseSrv: CaseSrv,
    observableSrv: ObservableSrv,
    organisationSrv: OrganisationSrv,
    override val publicData: PublicAlert,
    implicit val db: Database,
    @Named("v0") override val queryExecutor: QueryExecutor
) extends QueryCtrl {
  def create: Action[AnyContent] =
    entrypoint("create alert")
      .extract("alert", FieldsParser[InputAlert])
      .extract("caseTemplate", FieldsParser[String].optional.on("caseTemplate"))
      .extract("observables", FieldsParser[InputObservable].sequence.on("artifacts"))
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplateName: Option[String]  = request.body("caseTemplate")
        val inputAlert: InputAlert            = request.body("alert")
        val observables: Seq[InputObservable] = request.body("observables")
        val customFields                      = inputAlert.customFields.map(c => InputCustomFieldValue(c.name, c.value, c.order))
        val caseTemplate                      = caseTemplateName.flatMap(ct => caseTemplateSrv.get(EntityIdOrName(ct)).visible.headOption)
        for {
          organisation <-
            userSrv
              .current
              .organisations(Permissions.manageAlert)
              .get(request.organisation)
              .orFail(AuthorizationError("Operation not permitted"))
          richAlert          <- alertSrv.create(inputAlert.toAlert, organisation, inputAlert.tags, customFields, caseTemplate)
          createdObservables <- auditSrv.mergeAudits(observables.toTry(createObservable(richAlert.alert, _)).map(_.flatten))(_ => Success(()))
        } yield Results.Created((richAlert -> createdObservables).toJson)
      }

  def alertSimilarityRenderer(implicit
      authContext: AuthContext
  ): Traversal.V[Alert] => Traversal[JsArray, JList[JMap[String, Any]], Converter[JsArray, JList[JMap[String, Any]]]] =
    _.similarCases(organisationSrv, caseFilter = None)
      .fold
      .domainMap { similarCases =>
        JsArray {
          similarCases.map {
            case (richCase, similarStats) =>
              val similarCase = richCase
                .into[OutputSimilarCase]
                .withFieldConst(_.artifactCount, similarStats.observable._2)
                .withFieldConst(_.iocCount, similarStats.ioc._2)
                .withFieldConst(_.similarArtifactCount, similarStats.observable._1)
                .withFieldConst(_.similarIocCount, similarStats.ioc._1)
                .withFieldComputed(_._id, _._id.toString)
                .withFieldComputed(_.id, _._id.toString)
                .withFieldRenamed(_.number, _.caseId)
                .withFieldComputed(_.status, _.status.toString)
                .withFieldComputed(_.tags, _.tags.toSet)
                .enableMethodAccessors
                .transform
              Json.toJson(similarCase)
          }
        }
      }

  def get(alertId: String): Action[AnyContent] =
    entrypoint("get alert")
      .extract("similarity", FieldsParser[Boolean].optional.on("similarity"))
      .authRoTransaction(db) { implicit request => implicit graph =>
        val similarity: Option[Boolean] = request.body("similarity")
        val alert =
          alertSrv
            .get(EntityIdOrName(alertId))
            .visible(organisationSrv)
        if (similarity.contains(true))
          alert
            .richAlertWithCustomRenderer(alertSimilarityRenderer(request))
            .getOrFail("Alert")
            .map {
              case (richAlert, similarCases) =>
                val alertWithObservables: (RichAlert, Seq[RichObservable]) =
                  richAlert -> observableSrv.startTraversal.relatedTo(richAlert._id).richObservableWithSeen(organisationSrv).toSeq

                Results.Ok(alertWithObservables.toJson.as[JsObject] + ("similarCases" -> similarCases))
            }
        else
          alert
            .richAlert
            .getOrFail("Alert")
            .map { richAlert =>
              val alertWithObservables: (RichAlert, Seq[RichObservable]) =
                richAlert -> alertSrv.get(richAlert.alert).observables.richObservable.toSeq
              Results.Ok(alertWithObservables.toJson)
            }

      }

  def update(alertIdOrName: String): Action[AnyContent] =
    entrypoint("update alert")
      .extract("alert", FieldsParser.update("alert", publicData.publicProperties))
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("alert")
        alertSrv
          .update(_.get(EntityIdOrName(alertIdOrName)).visible(organisationSrv), propertyUpdaters)
          .flatMap { case (alertSteps, _) => alertSteps.richAlert.getOrFail("Alert") }
          .map { richAlert =>
            val alertWithObservables: (RichAlert, Seq[RichObservable]) = richAlert -> alertSrv.get(richAlert.alert).observables.richObservable.toSeq
            Results.Ok(alertWithObservables.toJson)
          }
      }

  def delete(alertIdOrName: String): Action[AnyContent] =
    entrypoint("delete alert")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertIdOrName))
              .visible(organisationSrv)
              .getOrFail("Alert")
          _ <- alertSrv.remove(alert)
        } yield Results.NoContent
      }

  def bulkDelete: Action[AnyContent] =
    entrypoint("bulk delete alerts")
      .extract("ids", FieldsParser.string.sequence.on("ids"))
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        val ids: Seq[String] = request.body("ids")
        ids
          .toTry { alertId =>
            for {
              alert <-
                alertSrv
                  .get(EntityIdOrName(alertId))
                  .visible(organisationSrv)
                  .getOrFail("Alert")
              _ <- alertSrv.remove(alert)
            } yield ()
          }
          .map(_ => Results.NoContent)
      }

  def mergeWithCase(alertIdOrName: String, caseIdOrName: String): Action[AnyContent] =
    entrypoint("merge alert with case")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        for {
          alert    <- alertSrv.get(EntityIdOrName(alertIdOrName)).visible(organisationSrv).getOrFail("Alert")
          case0    <- caseSrv.get(EntityIdOrName(caseIdOrName)).can(Permissions.manageCase).getOrFail("Case")
          _        <- alertSrv.mergeInCase(alert, case0)
          richCase <- caseSrv.get(EntityIdOrName(caseIdOrName)).richCase.getOrFail("Case")
        } yield Results.Ok(richCase.toJson)
      }

  def bulkMergeWithCase: Action[AnyContent] =
    entrypoint("bulk merge with case")
      .extract("caseId", FieldsParser.string.on("caseId"))
      .extract("alertIds", FieldsParser.string.sequence.on("alertIds"))
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        val alertIds: Seq[String] = request.body("alertIds")
        val caseId: String        = request.body("caseId")

        val destinationCase = caseSrv
          .get(EntityIdOrName(caseId))
          .can(Permissions.manageCase)
          .getOrFail("Case")

        alertIds
          .foldLeft(destinationCase) { (caseTry, alertId) =>
            for {
              case0 <- caseTry
              alert <-
                alertSrv
                  .get(EntityIdOrName(alertId))
                  .visible(organisationSrv)
                  .getOrFail("Alert")
              updatedCase <- alertSrv.mergeInCase(alert, case0)
            } yield updatedCase
          }
          .flatMap(c => caseSrv.get(c._id).richCase.getOrFail("Case"))
          .map(rc => Results.Ok(rc.toJson))
      }

  def markAsRead(alertId: String): Action[AnyContent] =
    entrypoint("mark alert as read")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .visible(organisationSrv)
              .getOrFail("Alert")
          _ <- alertSrv.markAsRead(alert._id)
          alertWithObservables <-
            alertSrv
              .get(alert)
              .project(_.by(_.richAlert).by(_.observables.richObservable.fold))
              .getOrFail("Alert")
        } yield Results.Ok(alertWithObservables.toJson)
      }

  def markAsUnread(alertId: String): Action[AnyContent] =
    entrypoint("mark alert as unread")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .visible(organisationSrv)
              .getOrFail("Alert")
          _ <- alertSrv.markAsUnread(alert._id)
          alertWithObservables <-
            alertSrv
              .get(alert)
              .project(_.by(_.richAlert).by(_.observables.richObservable.fold))
              .getOrFail("Alert")
        } yield Results.Ok(alertWithObservables.toJson)
      }

  def createCase(alertId: String): Action[AnyContent] =
    entrypoint("create case from alert")
      .extract("caseTemplate", FieldsParser.string.optional.on("caseTemplate"))
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        val caseTemplate: Option[String] = request.body("caseTemplate")
        for {
          organisation <- organisationSrv.current.getOrFail("Organisation")
          alert <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .visible(organisationSrv)
              .richAlert
              .getOrFail("Alert")
          _ <- caseTemplate.map(ct => caseTemplateSrv.get(EntityIdOrName(ct)).visible.existsOrFail).flip
          alertWithCaseTemplate = caseTemplate.fold(alert)(ct => alert.copy(caseTemplate = Some(ct)))
          assignee <- if (request.isPermitted(Permissions.manageCase)) userSrv.current.getOrFail("User").map(Some(_)) else Success(None)
          richCase <- alertSrv.createCase(alertWithCaseTemplate, assignee, organisation)
        } yield Results.Created(richCase.toJson)
      }

  def followAlert(alertId: String): Action[AnyContent] =
    entrypoint("follow alert")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .visible(organisationSrv)
              .getOrFail("Alert")
          _ <- alertSrv.followAlert(alert._id)
          alertWithObservables <-
            alertSrv
              .get(alert)
              .project(_.by(_.richAlert).by(_.observables.richObservable.fold))
              .getOrFail("Alert")
        } yield Results.Ok(alertWithObservables.toJson)
      }

  def unfollowAlert(alertId: String): Action[AnyContent] =
    entrypoint("unfollow alert")
      .authPermittedTransaction(db, Permissions.manageAlert) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .visible(organisationSrv)
              .getOrFail("Alert")
          _ <- alertSrv.unfollowAlert(alert._id)
          alertWithObservables <-
            alertSrv
              .get(alert)
              .project(_.by(_.richAlert).by(_.observables.richObservable.fold))
              .getOrFail("Alert")
        } yield Results.Ok(alertWithObservables.toJson)
      }

  private def createObservable(alert: Alert with Entity, observable: InputObservable)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Seq[RichObservable]] =
    observableTypeSrv
      .getOrFail(EntityName(observable.dataType))
      .flatMap {
        case attachmentType if attachmentType.isAttachment =>
          observable.data.map(_.split(';')).toTry {
            case Array(filename, contentType, value) =>
              val data = Base64.getDecoder.decode(value)
              attachmentSrv
                .create(filename, contentType, data)
                .flatMap(attachment => alertSrv.createObservable(alert, observable.toObservable, attachment))
            case Array(filename, contentType) =>
              attachmentSrv
                .create(filename, contentType, Array.emptyByteArray)
                .flatMap(attachment => alertSrv.createObservable(alert, observable.toObservable, attachment))
            case data =>
              Failure(InvalidFormatAttributeError("artifacts.data", "filename;contentType;base64value", Set.empty, FString(data.mkString(";"))))
          }
        case _ =>
          observable
            .data
            .toTry(d => alertSrv.createObservable(alert, observable.toObservable, d))
      }
}

@Singleton
class PublicAlert @Inject() (
    alertSrv: AlertSrv,
    organisationSrv: OrganisationSrv,
    customFieldSrv: CustomFieldSrv,
    db: Database
) extends PublicData {
  override val entityName: String = "alert"
  override val initialQuery: Query =
    Query
      .init[Traversal.V[Alert]](
        "listAlert",
        (graph, authContext) => alertSrv.startTraversal(graph).visible(organisationSrv)(authContext)
      )
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Alert]](
    "getAlert",
    (idOrName, graph, authContext) => alertSrv.get(idOrName)(graph).visible(organisationSrv)(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Alert], IteratorOutput](
      "page",
      (range, alertSteps, _) =>
        alertSteps
          .richPage(range.from, range.to, withTotal = true) { alerts =>
            alerts.project(_.by(_.richAlert).by(_.observables.richObservable.fold))
          }
    )
  override val outputQuery: Query = Query.output[RichAlert, Traversal.V[Alert]](_.richAlert)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Alert], Traversal.V[Case]]("cases", (alertSteps, _) => alertSteps.`case`),
    Query[Traversal.V[Alert], Traversal.V[Observable]]("observables", (alertSteps, _) => alertSteps.observables),
    Query[
      Traversal.V[Alert],
      Traversal[(RichAlert, Seq[RichObservable]), JMap[String, Any], Converter[(RichAlert, Seq[RichObservable]), JMap[String, Any]]]
    ](
      "withObservables",
      (alertSteps, _) =>
        alertSteps
          .project(
            _.by(_.richAlert)
              .by(_.observables.richObservable.fold)
          )
    ),
    Query.output[(RichAlert, Seq[RichObservable])]
  )

  def statusFilter(status: String): Traversal.V[Alert] => Traversal.V[Alert] =
    status match {
      case "New"      => _.isEmptyId(_.caseId).has(_.read, false)
      case "Updated"  => _.nonEmptyId(_.caseId).has(_.read, false)
      case "Ignored"  => _.isEmptyId(_.caseId).has(_.read, true)
      case "Imported" => _.nonEmptyId(_.caseId).has(_.read, true)
      case _          => _.empty
    }

  def statusNotFilter(status: String): Traversal.V[Alert] => Traversal.V[Alert] =
    status match {
      case "New"      => _.or(_.nonEmptyId(_.caseId), _.has(_.read, true))
      case "Updated"  => _.or(_.isEmptyId(_.caseId), _.has(_.read, true))
      case "Ignored"  => _.or(_.nonEmptyId(_.caseId), _.has(_.read, false))
      case "Imported" => _.or(_.isEmptyId(_.caseId), _.has(_.read, false))
      case _          => identity
    }

  override val publicProperties: PublicProperties =
    PublicPropertyListBuilder[Alert]
      .property("type", UMapping.string)(_.field.updatable)
      .property("source", UMapping.string)(_.field.updatable)
      .property("sourceRef", UMapping.string)(_.field.updatable)
      .property("title", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .property("severity", UMapping.int)(_.field.updatable)
      .property("date", UMapping.date)(_.field.updatable)
      .property("lastSyncDate", UMapping.date.optional)(_.field.updatable)
      .property("tags", UMapping.string.set)(
        _.field
          .custom { (_, value, vertex, graph, authContext) =>
            alertSrv
              .get(vertex)(graph)
              .getOrFail("Alert")
              .flatMap(alert => alertSrv.updateTags(alert, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("pap", UMapping.int)(_.field.updatable)
      .property("read", UMapping.boolean)(_.field.updatable)
      .property("follow", UMapping.boolean)(_.field.updatable)
      .property("status", UMapping.string)(
        _.select { alerts =>
          val readAndCase = alerts.project(
            _.byValue(_.read)
              .by(_.`case`.limit(1).count)
          )
          readAndCase.graphMap[String, String, IdentityConverter[String]](
            jmap =>
              readAndCase.converter.apply(jmap) match {
                case (false, caseCount) if caseCount == 0L => "New"
                case (false, _)                            => "Updated"
                case (true, caseCount) if caseCount == 0L  => "Ignored"
                case (true, _)                             => "Imported"
              },
            Converter.identity[String]
          )
        }
          .filter[String] {
            case (_, alerts, _, Right(predicate)) =>
              predicate.getBiPredicate.asInstanceOf[BiPredicate[_, _]] match {
                case Compare.eq       => statusFilter(predicate.getValue)(alerts)
                case Compare.neq      => statusNotFilter(predicate.getValue)(alerts)
                case Contains.within  => alerts.or(predicate.getValue.asInstanceOf[JList[String]].asScala.map(statusFilter): _*)
                case Contains.without => predicate.getValue.asInstanceOf[JList[String]].asScala.map(statusNotFilter).foldRight(alerts)(_ apply _)
                case p =>
                  logger.error(s"The predicate $p is not supported for alert status")
                  alerts.empty
              }
            case (_, alerts, _, Left(true)) => alerts
            case (_, alerts, _, _)          => alerts.empty
          }
          .readonly
      )
      .property("summary", UMapping.string.optional)(_.field.updatable)
      .property("user", UMapping.string)(_.field.updatable)
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(idOrName, _)), alerts) =>
          alerts
            .customFields(EntityIdOrName(idOrName))
            .jsonValue
        case (_, alerts) => alerts.customFields.nameJsonValue.fold.domainMap(JsObject(_))
      }
        .filter[JsValue] {
          case (FPathElem(_, FPathElem(name, _)), alerts, _, predicate) =>
            predicate match {
              case Right(predicate) => alerts.customFieldFilter(customFieldSrv, EntityIdOrName(name), predicate)
              case Left(true)       => alerts.hasCustomField(customFieldSrv, EntityIdOrName(name))
              case Left(false)      => alerts.hasNotCustomField(customFieldSrv, EntityIdOrName(name))
            }
          case (_, caseTraversal, _, _) => caseTraversal.empty
        }
        .custom {
          case (FPathElem(_, FPathElem(name, _)), value, vertex, graph, authContext) =>
            for {
              c <- alertSrv.getByIds(EntityId(vertex.id))(graph).getOrFail("Alert")
              _ <- alertSrv.setOrCreateCustomField(c, InputCustomFieldValue(name, Some(value), None))(graph, authContext)
            } yield Json.obj(s"customField.$name" -> value)
          case (FPathElem(_, FPathEmpty), values: JsObject, vertex, graph, authContext) =>
            for {
              c   <- alertSrv.get(vertex)(graph).getOrFail("Alert")
              cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(EntityIdOrName(n))(graph).map(_ -> v) }
              _   <- alertSrv.updateCustomField(c, cfv)(graph, authContext)
            } yield Json.obj("customFields" -> values)
          case _ => Failure(BadRequestError("Invalid custom fields format"))
        })
      .property("case", db.idMapping)(_.select(_.`case`._id).readonly)
      .property("imported", UMapping.boolean)(
        _.select(_.imported)
          .filter[Boolean]((_, alertTraversal, _, predicate) =>
            predicate.fold(
              b => if (b) alertTraversal else alertTraversal.empty,
              p =>
                if (p.getValue) alertTraversal.nonEmptyId(_.caseId)
                else alertTraversal.isEmptyId(_.caseId)
            )
          )
          .readonly
      )
      .property("importDate", UMapping.date.optional)(_.select(_.importDate).readonly)
      .property("computed.handlingDuration", UMapping.long)(_.select(_.handlingDuration).readonly)
      .property("computed.handlingDurationInSeconds", UMapping.long)(_.select(_.handlingDuration.math("_ / 1000").domainMap(_.toLong)).readonly)
      .property("computed.handlingDurationInMinutes", UMapping.long)(_.select(_.handlingDuration.math("_ / 60000").domainMap(_.toLong)).readonly)
      .property("computed.handlingDurationInHours", UMapping.long)(_.select(_.handlingDuration.math("_ / 3600000").domainMap(_.toLong)).readonly)
      .property("computed.handlingDurationInDays", UMapping.long)(_.select(_.handlingDuration.math("_ / 86400000").domainMap(_.toLong)).readonly)
      .build
}
