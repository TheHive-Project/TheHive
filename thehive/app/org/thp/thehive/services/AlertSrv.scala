package org.thp.thehive.services

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal._
import org.thp.scalligraph.utils.FunctionalCondition.When
import org.thp.scalligraph.{BadRequestError, CreateError, EntityId, EntityIdOrName, RichOptionTry, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.String64
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models._
import play.api.libs.json.{JsObject, JsValue, Json}

import java.lang.{Long => JLong}
import java.util.{Date, Map => JMap}
import scala.util.{Failure, Success, Try}

class AlertSrv(
    caseSrv: CaseSrv,
    tagSrv: TagSrv,
    organisationSrv: OrganisationSrv,
    customFieldSrv: CustomFieldSrv,
    customFieldValueSrv: CustomFieldValueSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableSrv: ObservableSrv,
    auditSrv: AuditSrv,
    attachmentSrv: AttachmentSrv,
    integrityCheckActor: => ActorRef @@ IntegrityCheckTag
) extends VertexSrv[Alert]
    with TheHiveOpsNoDeps {

  val alertTagSrv              = new EdgeSrv[AlertTag, Alert, Tag]
  val alertCustomFieldValueSrv = new EdgeSrv[AlertCustomFieldValue, Alert, CustomFieldValue]
  val alertOrganisationSrv     = new EdgeSrv[AlertOrganisation, Alert, Organisation]
  val alertCaseSrv             = new EdgeSrv[AlertCase, Alert, Case]
  val alertCaseTemplateSrv     = new EdgeSrv[AlertCaseTemplate, Alert, CaseTemplate]
  val alertObservableSrv       = new EdgeSrv[AlertObservable, Alert, Observable]

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Alert] =
    name.split(';') match {
      case Array(tpe, source, sourceRef) => startTraversal.getBySourceId(tpe, source, sourceRef)
      case _                             => startTraversal.empty
    }

  def create(
      alert: Alert,
      organisation: Organisation with Entity,
      tagNames: Set[String],
      customFields: Seq[InputCustomFieldValue],
      caseTemplate: Option[CaseTemplate with Entity]
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichAlert] =
    tagNames.toTry(tagSrv.getOrCreate).flatMap(create(alert, organisation, _, customFields, caseTemplate))

  private def create(
      alert: Alert,
      organisation: Organisation with Entity,
      tags: Seq[Tag with Entity],
      customFields: Seq[InputCustomFieldValue],
      caseTemplate: Option[CaseTemplate with Entity]
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichAlert] = {
    val alertAlreadyExist = startTraversal.getBySourceId(alert.`type`, alert.source, alert.sourceRef).inOrganisation(organisation._id).exists
    if (alertAlreadyExist)
      Failure(CreateError(s"Alert ${alert.`type`}:${alert.source}:${alert.sourceRef} already exist in organisation ${organisation.name}"))
    else
      for {
        createdAlert <- createEntity(alert.copy(organisationId = organisation._id))
        _            <- alertOrganisationSrv.create(AlertOrganisation(), createdAlert, organisation)
        _            <- caseTemplate.map(ct => alertCaseTemplateSrv.create(AlertCaseTemplate(), createdAlert, ct)).flip
        _            <- tags.toTry(t => alertTagSrv.create(AlertTag(), createdAlert, t))
        cfs          <- customFields.toTry { cf: InputCustomFieldValue => createCustomField(createdAlert, cf) }
        richAlert = RichAlert(createdAlert, cfs, None, caseTemplate.map(_.name), 0)
        _ <- auditSrv.alert.create(createdAlert, richAlert.toJson)
      } yield richAlert
  }

  override def update(
      traversal: Traversal.V[Alert],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Alert], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (alerts, updatedFields) =>
        alerts
          .clone()
          .getOrFail("Alert")
          .flatMap(auditSrv.alert.update(_, updatedFields))
    }

  def updateTags(alert: Alert with Entity, tags: Set[String])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[(Seq[Tag with Entity], Seq[Tag with Entity])] =
    for {
      tagsToAdd <- (tags -- alert.tags).toTry(tagSrv.getOrCreate)
      tagsToRemove = get(alert).tags.toSeq.filterNot(t => tags.contains(t.toString))
      _ <- tagsToAdd.toTry(alertTagSrv.create(AlertTag(), alert, _))
      _ = if (tags.nonEmpty) get(alert).outE[AlertTag].filter(_.otherV().hasId(tagsToRemove.map(_._id): _*)).remove()
      _ <- get(alert).update(_.tags, tags.toSeq).getOrFail("Alert")
      _ <- auditSrv.alert.update(alert, Json.obj("tags" -> tags))
    } yield (tagsToAdd, tagsToRemove)

  def addTags(alert: Alert with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    updateTags(alert, tags ++ alert.tags).map(_ => ())

  def createObservable(alert: Alert with Entity, observable: Observable, data: String)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- observableSrv.create(observable.copy(organisationIds = Set(organisationSrv.currentId), relatedId = alert._id), data)
      _                 <- alertObservableSrv.create(AlertObservable(), alert, createdObservable.observable)
      _                 <- auditSrv.observableInAlert.create(createdObservable.observable, alert, createdObservable.toJson)
    } yield createdObservable

  def createObservable(alert: Alert with Entity, observable: Observable, attachment: Attachment with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- observableSrv.create(observable.copy(organisationIds = Set(organisationSrv.currentId), relatedId = alert._id), attachment)
      _                 <- alertObservableSrv.create(AlertObservable(), alert, createdObservable.observable)
      _                 <- auditSrv.observableInAlert.create(createdObservable.observable, alert, createdObservable.toJson)
    } yield createdObservable

  def createObservable(alert: Alert with Entity, observable: Observable, file: FFile)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    attachmentSrv.create(file).flatMap(attachment => createObservable(alert, observable, attachment))

  def createCustomField(
      alert: Alert with Entity,
      customField: InputCustomFieldValue
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    customFieldSrv
      .getOrFail(EntityIdOrName(customField.name.value))
      .flatMap(cf => createCustomField(alert, cf, customField.value, customField.order))

  def createCustomField(
      alert: Alert with Entity,
      customField: CustomField with Entity,
      customFieldValue: JsValue,
      order: Option[Int]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    for {
      cfv <- customFieldValueSrv.createCustomField(alert, customField, customFieldValue, order)
      _   <- alertCustomFieldValueSrv.create(AlertCustomFieldValue(), alert, cfv)
    } yield RichCustomField(customField, cfv)

  def updateOrCreateCustomField(
      alert: Alert with Entity,
      customField: CustomField with Entity,
      value: JsValue,
      order: Option[Int]
  )(implicit graph: Graph, authContext: AuthContext): Try[RichCustomField] =
    get(alert)
      .customFieldValue
      .has(_.name, customField.name)
      .headOption
      .fold(createCustomField(alert, customField, value, order)) { cfv =>
        customFieldValueSrv
          .updateValue(cfv, customField.`type`, value, order)
          .map(RichCustomField(customField, _))
      }

  def markAsUnread(alertId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      alert <- get(alertId).update[Boolean](_.read, false).getOrFail("Alert")
      _     <- auditSrv.alert.update(alert, Json.obj("read" -> false))
    } yield ()

  def markAsRead(alertId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      alert <- get(alertId).update[Boolean](_.read, true).getOrFail("Alert")
      _     <- auditSrv.alert.update(alert, Json.obj("read" -> true))
    } yield ()

  def followAlert(alertId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      alert <- get(alertId).update[Boolean](_.follow, true).getOrFail("Alert")
      _     <- auditSrv.alert.update(alert, Json.obj("follow" -> true))
    } yield ()

  def unfollowAlert(alertId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      alert <- get(alertId).update[Boolean](_.follow, false).getOrFail("Alert")
      _     <- auditSrv.alert.update(alert, Json.obj("follow" -> false))
    } yield ()

  def createCase(
      alert: RichAlert,
      assignee: Option[User with Entity],
      sharingParameters: Map[String, SharingParameter],
      taskRule: Option[String],
      observableRule: Option[String]
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichCase] =
    auditSrv.mergeAudits {
      get(alert.alert).`case`.richCase.getOrFail("Case").orElse {
        for {
          caseTemplate <-
            alert
              .caseTemplate
              .map(ct => caseTemplateSrv.get(EntityIdOrName(ct)).visible.richCaseTemplate.getOrFail("CaseTemplate"))
              .flip
          customField = alert.customFields.map(f => InputCustomFieldValue(String64("customField.name", f.name), f.jsValue, f.order))
          case0 = Case(
            title = caseTemplate.flatMap(_.titlePrefix).getOrElse("") + alert.title,
            description = alert.description,
            severity = alert.severity,
            startDate = new Date,
            endDate = None,
            flag = false,
            tlp = alert.tlp,
            pap = alert.pap,
            status = CaseStatus.Open,
            summary = None,
            alert.tags
          )

          createdCase <- caseSrv.create(case0, assignee, customField, caseTemplate, Nil, sharingParameters, taskRule, observableRule)
          _           <- importObservables(alert.alert, createdCase.`case`)
          _           <- alertCaseSrv.create(AlertCase(), alert.alert, createdCase.`case`)
          _           <- get(alert.alert).update(_.caseId, createdCase._id).getOrFail("Alert")
          _           <- markAsRead(alert._id)
          _ = integrityCheckActor ! EntityAdded("Alert")
        } yield createdCase
      }
    }(richCase => auditSrv.alert.createCase(alert.alert, richCase.`case`, richCase.toJson.as[JsObject]))

  def mergeInCase(alertId: EntityIdOrName, caseId: EntityIdOrName)(implicit graph: Graph, authContext: AuthContext): Try[Case with Entity] =
    for {
      alert       <- getOrFail(alertId)
      case0       <- caseSrv.getOrFail(caseId)
      updatedCase <- mergeInCase(alert, case0)
    } yield updatedCase

  def mergeInCase(alert: Alert with Entity, `case`: Case with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Case with Entity] =
    if (get(alert).isImported)
      Failure(BadRequestError("Alert is already imported"))
    else
      auditSrv
        .mergeAudits {
          // No audit for markAsRead and observables
          // Audits for customFields, description and tags
          val description = `case`.description + s"\n  \n#### Merged with alert #${alert.sourceRef} ${alert.title}\n\n${alert.description.trim}"
          for {
            _ <- markAsRead(alert._id)
            _ <- importObservables(alert, `case`)
            _ <- importCustomFields(alert, `case`)
            _ <- caseSrv.addTags(`case`, alert.tags.toSet)
            _ <- alertCaseSrv.create(AlertCase(), alert, `case`)
            _ <- get(alert).update(_.caseId, `case`._id).getOrFail("Alert")
            c <- caseSrv.get(`case`).update(_.description, description).getOrFail("Case")
            details <- Success(
              Json.obj(
                "customFields" -> get(alert).richCustomFields.toSeq.map(_.toOutput.toJson),
                "description"  -> c.description,
                "tags"         -> (`case`.tags ++ alert.tags).distinct
              )
            )
          } yield details
        }(details => auditSrv.alert.mergeToCase(alert, `case`, details.as[JsObject]))
        .map(_ => integrityCheckActor ! EntityAdded("Alert"))
        .flatMap(_ => caseSrv.getOrFail(`case`._id))

  def importObservables(alert: Alert with Entity, `case`: Case with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    get(alert)
      .observables
      .richObservable
      .toIterator
      .toTry { richObservable =>
        richObservable
          .dataOrAttachment
          .fold(
            data => caseSrv.createObservable(`case`, richObservable.observable, data),
            attachment => caseSrv.createObservable(`case`, richObservable.observable, attachment)
          )
          .recover {
            case _: CreateError => // if case already contains observable, update tags
              richObservable
                .dataOrAttachment
                .fold(
                  data => observableSrv.startTraversal.filterOnData(data),
                  attachment => observableSrv.startTraversal.filterOnAttachmentId(attachment.attachmentId)
                )
                .filterOnData(richObservable.dataType)
                .relatedTo(`case`._id)
                .inOrganisation(organisationSrv.currentId)
                .headOption
                .foreach { observable =>
                  val newTags = (observable.tags ++ richObservable.tags).toSet
                  observableSrv.updateTags(observable, newTags)
                }
          }
      }
      .map(_ => ())

  def importCustomFields(alert: Alert with Entity, `case`: Case with Entity)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    get(alert)
      .richCustomFields
      .toIterator
      .toTry(caseSrv.createCustomField(`case`, _))
      .map(_ => ())

  def remove(alert: Alert with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    auditSrv.mergeAudits {
      get(alert).observables.toIterator.foreach(observableSrv.delete(_))
      Success(())
    } { _ =>
      for {
        organisation <- organisationSrv.getOrFail(authContext.organisation)
        _            <- auditSrv.alert.delete(alert, organisation)
      } yield get(alert).remove()
    }
}

trait AlertOpsNoDeps { ops: TheHiveOpsNoDeps =>

  implicit class AlertOpsNoDepsDefs(traversal: Traversal.V[Alert])
      extends EntityWithCustomFieldOpsNoDepsDefs[Alert, AlertCustomFieldValue](traversal, ops) {
    def get(idOrSource: EntityIdOrName): Traversal.V[Alert] =
      idOrSource.fold(
        traversal.getByIds(_),
        _.split(';') match {
          case Array(tpe, source, sourceRef) => getBySourceId(tpe, source, sourceRef)
          case _                             => traversal.empty
        }
      )

    def getBySourceId(`type`: String, source: String, sourceRef: String): Traversal.V[Alert] =
      traversal
        .has(_.`type`, `type`)
        .has(_.source, source)
        .has(_.sourceRef, sourceRef)

    def inOrganisation(organisationId: EntityId): Traversal.V[Alert] =
      traversal.has(_.organisationId, organisationId)

    def filterByType(`type`: String): Traversal.V[Alert] = traversal.has(_.`type`, `type`)

    def filterBySource(source: String): Traversal.V[Alert] = traversal.has(_.source, source)

    def organisation: Traversal.V[Organisation] = traversal.out[AlertOrganisation].v[Organisation]

    def tags: Traversal.V[Tag] = traversal.out[AlertTag].v[Tag]

    def `case`: Traversal.V[Case] = traversal.out[AlertCase].v[Case]

    def imported: Traversal[Boolean, Boolean, IdentityConverter[Boolean]] =
      traversal.choose(_.nonEmptyId(_.caseId), onTrue = true, onFalse = false)

    def isImported: Boolean =
      traversal.nonEmptyId(_.caseId).exists

    def importDate: Traversal[Date, Date, Converter[Date, Date]] =
      traversal.outE[AlertCase].value(_._createdAt)

    def handlingDuration: Traversal[Long, Long, IdentityConverter[Long]] =
      traversal.coalesceIdent(
        _.filter(_.outE[AlertCase])
          .sack(
            (_: JLong, importDate: JLong) => importDate,
            _.by(_.importDate.graphMap[Long, JLong, Converter[Long, JLong]](_.getTime, Converter.long))
          )
          .sack((_: Long) - (_: JLong), _.by(_._createdAt.graphMap[Long, JLong, Converter[Long, JLong]](_.getTime, Converter.long)))
          .sack[Long],
        _.constant(0L)
      )

    def observables: Traversal.V[Observable] = traversal.out[AlertObservable].v[Observable]

    def caseTemplate: Traversal.V[CaseTemplate] = traversal.out[AlertCaseTemplate].v[CaseTemplate]

    def richAlertWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Alert] => Traversal[D, G, C]
    ): Traversal[(RichAlert, D), JMap[String, Any], Converter[(RichAlert, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.richCustomFields.fold)
            .by(_.`case`._id.option)
            .by(_.caseTemplate.value(_.name).option)
            .by(entityRenderer)
        )
        .domainMap {
          case (alert, customFields, caseId, caseTemplate, renderedEntity) =>
            val observableCount = traversal
              .graph
              .indexCountQuery(
                s"""v."_label":Observable AND """ +
                  s"v.relatedId:${traversal.graph.escapeQueryParameter(alert._id.value)}"
              )
            RichAlert(
              alert,
              customFields,
              caseId,
              caseTemplate,
              observableCount
            ) -> renderedEntity
        }

    def richAlert: Traversal[RichAlert, JMap[String, Any], Converter[RichAlert, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.richCustomFields.fold)
            .by(_.`case`._id.option)
            .by(_.caseTemplate.value(_.name).option)
            .by(_.outE[AlertObservable].count)
        )
        .domainMap {
          case (alert, customFields, caseId, caseTemplate, observableCount) =>
            RichAlert(
              alert,
              customFields,
              caseId,
              caseTemplate,
              observableCount
            )
        }
  }
}

trait AlertOps { ops: TheHiveOps =>
  protected val organisationSrv: OrganisationSrv
  protected val customFieldSrv: CustomFieldSrv

  implicit class AlertOpsDefs(traversal: Traversal.V[Alert]) extends EntityWithCustomFieldOpsDefs[Alert, AlertCustomFieldValue](traversal, ops) {
    def visible(implicit authContext: AuthContext): Traversal.V[Alert] =
      traversal.has(_.organisationId, organisationSrv.currentId(traversal.graph, authContext))

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Alert] =
      if (authContext.permissions.contains(permission))
        traversal.visible
      else traversal.empty

    def similarCases(caseFilter: Option[Traversal.V[Case] => Traversal.V[Case]])(implicit
        authContext: AuthContext
    ): Traversal[(RichCase, SimilarStats), JMap[String, Any], Converter[(RichCase, SimilarStats), JMap[String, Any]]] =
      traversal
        .observables
        .filteredSimilar
        .visible
        .merge(caseFilter)((obs, cf) => obs.filter(o => cf(o.`case`)))
        .filter(_.in[ShareObservable])
        .group(_.by(_.`case`))
        .unfold
        .project(
          _.by(
            _.selectKeys
              .project(
                _.by(_.richCaseWithoutPerms)
                  .by((_: Traversal.V[Case]).observables.hasNot(_.ignoreSimilarity, true).groupCount(_.byValue(_.ioc)))
              )
          )
            .by(
              _.selectValues
                .project(
                  _.by(_.unfold.groupCount(_.byValue(_.ioc)))
                    .by(_.unfold.groupCount(_.by(_.typeName)))
                )
            )
        )
        .domainMap {
          case ((richCase, obsStats), (iocStats, observableTypeStats)) =>
            val obsStatsMap     = obsStats.view.mapValues(_.toInt).toMap
            val similarStatsMap = iocStats.view.mapValues(_.toInt).toMap
            richCase -> SimilarStats(
              similarStatsMap.values.sum         -> obsStatsMap.values.sum,
              similarStatsMap.getOrElse(true, 0) -> obsStatsMap.getOrElse(true, 0),
              observableTypeStats
            )
        }
  }

}
class AlertIntegrityCheckOps(val db: Database, val service: AlertSrv, caseSrv: CaseSrv, organisationSrv: OrganisationSrv)
    extends IntegrityCheckOps[Alert]
    with TheHiveOpsNoDeps {

  override def resolve(entities: Seq[Alert with Entity])(implicit graph: Graph): Try[Unit] = {
    val (imported, notImported) = entities.partition(_.caseId.isDefined)
    val remainingAlerts = if (imported.nonEmpty && notImported.nonEmpty) {
      // Remove all non imported alerts
      service.getByIds(notImported.map(_._id): _*).remove()
      imported
    } else entities
    // Keep the last created alert
    EntitySelector.lastCreatedEntity(remainingAlerts).foreach(e => service.getByIds(e._2.map(_._id): _*).remove())
    Success(())
  }

  override def globalCheck(): Map[String, Int] =
    db.tryTransaction { implicit graph =>
      Try {
        service
          .startTraversal
          .project(
            _.by
              .by(_.`case`._id.fold)
              .by(_.organisation._id.fold)
              .by(_.removeDuplicateOutEdges[AlertCase]())
              .by(_.removeDuplicateOutEdges[AlertOrganisation]())
          )
          .toIterator
          .map {
            case (alert, caseIds, orgIds, extraCaseEdges, extraOrgEdges) =>
              val caseStats = singleIdLink[Case]("caseId", caseSrv)(_.outEdge[AlertCase], _.set(EntityId.empty))
//                alert => cases => {
//                  service.get(alert).outE[AlertCase].filter(_.inV.hasId(cases.map(_._id): _*)).project(_.by.by(_.inV.v[Case])).toSeq
//              }
                .check(alert, alert.caseId, caseIds)
              val orgStats = singleIdLink[Organisation]("organisationId", organisationSrv)(_.outEdge[AlertOrganisation], _.remove)
                .check(alert, alert.organisationId, orgIds)

              caseStats <+> orgStats <+> extraCaseEdges <+> extraOrgEdges
          }
          .reduceOption(_ <+> _)
          .getOrElse(Map.empty)
      }
    }.getOrElse(Map("Alert-globalFailure" -> 1))
}
