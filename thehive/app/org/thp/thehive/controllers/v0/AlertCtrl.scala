package org.thp.thehive.controllers.v0

import java.util.{Base64, List => JList, Map => JMap}

import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, IteratorOutput, Traversal}
import org.thp.scalligraph.{AuthorizationError, BadRequestError, EntityId, EntityIdOrName, EntityName, InvalidFormatAttributeError, RichSeq}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.{InputAlert, InputObservable, OutputSimilarCase}
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Failure, Success, Try}

@Singleton
class AlertCtrl @Inject() (
    override val entrypoint: Entrypoint,
    alertSrv: AlertSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    attachmentSrv: AttachmentSrv,
    auditSrv: AuditSrv,
    userSrv: UserSrv,
    caseSrv: CaseSrv,
    override val publicData: PublicAlert,
    @Named("with-thehive-schema") implicit val db: Database,
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
          richObservables <- observables.toTry(createObservable).map(_.flatten)
          richAlert       <- alertSrv.create(inputAlert.toAlert, organisation, inputAlert.tags, customFields, caseTemplate)
          _               <- auditSrv.mergeAudits(richObservables.toTry(o => alertSrv.addObservable(richAlert.alert, o)))(_ => Success(()))
          createdObservables = alertSrv.get(richAlert.alert).observables.richObservable.toSeq
        } yield Results.Created((richAlert -> createdObservables).toJson)
      }

  def alertSimilarityRenderer(implicit
      authContext: AuthContext
  ): Traversal.V[Alert] => Traversal[JsArray, JList[JMap[String, Any]], Converter[JsArray, JList[JMap[String, Any]]]] =
    _.similarCases(None)
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
                .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
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
            .visible
        if (similarity.contains(true))
          alert
            .richAlertWithCustomRenderer(alertSimilarityRenderer(request))
            .getOrFail("Alert")
            .map {
              case (richAlert, similarCases) =>
                val alertWithObservables: (RichAlert, Seq[RichObservable]) =
                  richAlert -> alertSrv.get(richAlert.alert).observables.richObservableWithSeen.toSeq

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
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("alert")
        alertSrv
          .update(_.get(EntityIdOrName(alertIdOrName)).can(Permissions.manageAlert), propertyUpdaters)
          .flatMap { case (alertSteps, _) => alertSteps.richAlert.getOrFail("Alert") }
          .map { richAlert =>
            val alertWithObservables: (RichAlert, Seq[RichObservable]) = richAlert -> alertSrv.get(richAlert.alert).observables.richObservable.toSeq
            Results.Ok(alertWithObservables.toJson)
          }
      }

  def delete(alertIdOrName: String): Action[AnyContent] =
    entrypoint("delete alert")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertIdOrName))
              .can(Permissions.manageAlert)
              .getOrFail("Alert")
          _ <- alertSrv.remove(alert)
        } yield Results.NoContent
      }

  def bulkDelete: Action[AnyContent] =
    entrypoint("bulk delete alerts")
      .extract("ids", FieldsParser.string.sequence.on("ids"))
      .authTransaction(db) { implicit request => implicit graph =>
        val ids: Seq[String] = request.body("ids")
        ids
          .toTry { alertId =>
            for {
              alert <-
                alertSrv
                  .get(EntityIdOrName(alertId))
                  .can(Permissions.manageAlert)
                  .getOrFail("Alert")
              _ <- alertSrv.remove(alert)
            } yield ()
          }
          .map(_ => Results.NoContent)
      }

  def mergeWithCase(alertIdOrName: String, caseIdOrName: String): Action[AnyContent] =
    entrypoint("merge alert with case")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          alert    <- alertSrv.get(EntityIdOrName(alertIdOrName)).can(Permissions.manageAlert).getOrFail("Alert")
          case0    <- caseSrv.get(EntityIdOrName(caseIdOrName)).can(Permissions.manageCase).getOrFail("Case")
          _        <- alertSrv.mergeInCase(alert, case0)
          richCase <- caseSrv.get(EntityIdOrName(caseIdOrName)).richCase.getOrFail("Case")
        } yield Results.Ok(richCase.toJson)
      }

  def bulkMergeWithCase: Action[AnyContent] =
    entrypoint("bulk merge with case")
      .extract("caseId", FieldsParser.string.on("caseId"))
      .extract("alertIds", FieldsParser.string.sequence.on("alertIds"))
      .authTransaction(db) { implicit request => implicit graph =>
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
                  .can(Permissions.manageAlert)
                  .getOrFail("Alert")
              updatedCase <- alertSrv.mergeInCase(alert, case0)
            } yield updatedCase
          }
          .flatMap(c => caseSrv.get(c._id).richCase.getOrFail("Case"))
          .map(rc => Results.Ok(rc.toJson))
      }

  def markAsRead(alertId: String): Action[AnyContent] =
    entrypoint("mark alert as read")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .can(Permissions.manageAlert)
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
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .can(Permissions.manageAlert)
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
      .authTransaction(db) { implicit request => implicit graph =>
        val caseTemplate: Option[String] = request.body("caseTemplate")
        for {
          (alert, organisation) <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .can(Permissions.manageAlert)
              .alertUserOrganisation(Permissions.manageCase)
              .getOrFail("Alert")
          alertWithCaseTemplate = caseTemplate.fold(alert)(ct => alert.copy(caseTemplate = Some(ct)))
          richCase <- alertSrv.createCase(alertWithCaseTemplate, None, organisation)
        } yield Results.Created(richCase.toJson)
      }

  def followAlert(alertId: String): Action[AnyContent] =
    entrypoint("follow alert")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .can(Permissions.manageAlert)
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
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          alert <-
            alertSrv
              .get(EntityIdOrName(alertId))
              .can(Permissions.manageAlert)
              .getOrFail("Alert")
          _ <- alertSrv.unfollowAlert(alert._id)
          alertWithObservables <-
            alertSrv
              .get(alert)
              .project(_.by(_.richAlert).by(_.observables.richObservable.fold))
              .getOrFail("Alert")
        } yield Results.Ok(alertWithObservables.toJson)
      }

  private def createObservable(observable: InputObservable)(implicit
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
                .flatMap(attachment => observableSrv.create(observable.toObservable, attachmentType, attachment, observable.tags, Nil))
            case data =>
              Failure(InvalidFormatAttributeError("artifacts.data", "filename;contentType;base64value", Set.empty, FString(data.mkString(";"))))
          }
        case dataType => observable.data.toTry(d => observableSrv.create(observable.toObservable, dataType, d, observable.tags, Nil))
      }
}

@Singleton
class PublicAlert @Inject() (
    alertSrv: AlertSrv,
    organisationSrv: OrganisationSrv,
    customFieldSrv: CustomFieldSrv,
    @Named("with-thehive-schema") db: Database
) extends PublicData {
  override val entityName: String = "alert"
  override val initialQuery: Query =
    Query
      .init[Traversal.V[Alert]]("listAlert", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).alerts)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Alert]](
    "getAlert",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, authContext) => alertSrv.get(idOrName)(graph).visible(authContext)
  )
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Alert], IteratorOutput](
      "page",
      FieldsParser[OutputParam],
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
        _.select(_.tags.displayName)
          .filter((_, cases) =>
            cases
              .tags
              .graphMap[String, String, Converter.Identity[String]](
                { v =>
                  val namespace = UMapping.string.getProperty(v, "namespace")
                  val predicate = UMapping.string.getProperty(v, "predicate")
                  val value     = UMapping.string.optional.getProperty(v, "value")
                  Tag(namespace, predicate, value, None, 0).toString
                },
                Converter.identity[String]
              )
          )
          .converter(_ => Converter.identity[String])
          .custom { (_, value, vertex, _, graph, authContext) =>
            alertSrv
              .get(vertex)(graph)
              .getOrFail("Alert")
              .flatMap(alert => alertSrv.updateTagNames(alert, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("pap", UMapping.int)(_.field.updatable)
      .property("read", UMapping.boolean)(_.field.updatable)
      .property("follow", UMapping.boolean)(_.field.updatable)
      .property("status", UMapping.string)(
        _.select(
          _.project(
            _.byValue(_.read)
              .by(_.`case`.limit(1).count)
          ).domainMap {
            case (false, caseCount) if caseCount == 0L => "New"
            case (false, _)                            => "Updated"
            case (true, caseCount) if caseCount == 0L  => "Ignored"
            case (true, _)                             => "Imported"
          }
        ).readonly
      )
      .property("summary", UMapping.string.optional)(_.field.updatable)
      .property("user", UMapping.string)(_.field.updatable)
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), alertSteps) =>
          alertSteps.customFields(EntityIdOrName(name)).jsonValue
        case (_, alertSteps) => alertSteps.customFields.nameJsonValue.fold.domainMap(JsObject(_))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- alertSrv.getByIds(EntityId(vertex.id))(graph).getOrFail("Alert")
            _ <- alertSrv.setOrCreateCustomField(c, InputCustomFieldValue(name, Some(value), None))(graph, authContext)
          } yield Json.obj(s"customField.$name" -> value)
        case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
          for {
            c   <- alertSrv.get(vertex)(graph).getOrFail("Alert")
            cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(EntityIdOrName(n))(graph).map(_ -> v) }
            _   <- alertSrv.updateCustomField(c, cfv)(graph, authContext)
          } yield Json.obj("customFields" -> values)

        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })
      .property("case", db.idMapping)(_.select(_.`case`._id).readonly)
      .build
}
