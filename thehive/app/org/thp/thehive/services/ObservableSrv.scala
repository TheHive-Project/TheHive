package org.thp.thehive.services

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.Converter.Identity
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, StepLabel, Traversal}
import org.thp.scalligraph.utils.Hash
import org.thp.scalligraph.{BadRequestError, CreateError, EntityId, EntityIdOrName, EntityName, RichSeq}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import play.api.libs.json.{JsObject, Json}

import java.util.{Map => JMap}
import javax.inject.{Inject, Provider, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class ObservableSrv @Inject() (
    dataSrv: DataSrv,
    observableTypeSrv: ObservableTypeSrv,
    attachmentSrv: AttachmentSrv,
    tagSrv: TagSrv,
    auditSrv: AuditSrv,
    shareSrvProvider: Provider[ShareSrv],
    caseSrvProvider: Provider[CaseSrv],
    organisationSrv: OrganisationSrv,
    alertSrvProvider: Provider[AlertSrv]
) extends VertexSrv[Observable] {
  lazy val shareSrv: ShareSrv  = shareSrvProvider.get
  lazy val caseSrv: CaseSrv    = caseSrvProvider.get
  lazy val alertSrv: AlertSrv  = alertSrvProvider.get
  val observableDataSrv        = new EdgeSrv[ObservableData, Observable, Data]
  val observableObservableType = new EdgeSrv[ObservableObservableType, Observable, ObservableType]
  val observableAttachmentSrv  = new EdgeSrv[ObservableAttachment, Observable, Attachment]
  val observableTagSrv         = new EdgeSrv[ObservableTag, Observable, Tag]

  def create(observable: Observable, file: FFile)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    attachmentSrv.create(file).flatMap { attachment =>
      create(observable, attachment)
    }

  def create(
      observable: Observable,
      attachment: Attachment with Entity
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] = {
    val alreadyExists = startTraversal
      .has(_.organisationIds, organisationSrv.currentId)
      .has(_.relatedId, observable.relatedId)
      .has(_.dataType, observable.dataType)
      .filterOnAttachmentId(attachment.attachmentId)
      .exists
    if (alreadyExists) Failure(CreateError("Observable already exists"))
    else
      for {
        observableType <- observableTypeSrv.getOrFail(EntityName(observable.dataType))
        _ <-
          if (!observableType.isAttachment) Failure(BadRequestError("A text observable doesn't accept attachment"))
          else Success(())
        tags              <- observable.tags.toTry(tagSrv.getOrCreate)
        createdObservable <- createEntity(observable.copy(data = None))
        _                 <- observableObservableType.create(ObservableObservableType(), createdObservable, observableType)
        _                 <- observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)
        _                 <- tags.toTry(observableTagSrv.create(ObservableTag(), createdObservable, _))
      } yield RichObservable(createdObservable, Some(attachment), None, Nil)
  }

  def create(
      observable: Observable,
      dataValue: String
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] = {
    val alreadyExists = startTraversal
      .has(_.organisationIds, organisationSrv.currentId)
      .has(_.relatedId, observable.relatedId)
      .has(_.data, dataValue)
      .has(_.dataType, observable.dataType)
      .exists
    if (alreadyExists) Failure(CreateError("Observable already exists"))
    else
      for {
        observableType <- observableTypeSrv.getOrFail(EntityName(observable.dataType))
        _ <-
          if (observableType.isAttachment) Failure(BadRequestError("A attachment observable doesn't accept string value"))
          else Success(())
        tags              <- observable.tags.toTry(tagSrv.getOrCreate)
        data              <- dataSrv.create(Data(dataValue))
        createdObservable <- createEntity(observable.copy(data = Some(dataValue)))
        _                 <- observableObservableType.create(ObservableObservableType(), createdObservable, observableType)
        _                 <- observableDataSrv.create(ObservableData(), createdObservable, data)
        _                 <- tags.toTry(observableTagSrv.create(ObservableTag(), createdObservable, _))
      } yield RichObservable(createdObservable, None, None, Nil)
  }

  def addTags(observable: Observable with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Seq[Tag with Entity]] = {
    val currentTags = get(observable)
      .tags
      .toSeq
      .map(_.toString)
      .toSet
    for {
      createdTags <- (tags -- currentTags).filterNot(_.isEmpty).toTry(tagSrv.getOrCreate)
      _           <- createdTags.toTry(observableTagSrv.create(ObservableTag(), observable, _))
//      _           <- auditSrv.observable.update(observable, Json.obj("tags" -> (currentTags ++ tags)))
    } yield createdTags
  }

  def updateTags(observable: Observable with Entity, tags: Set[String])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[(Seq[Tag with Entity], Seq[Tag with Entity])] =
    for {
      tagsToAdd <- (tags -- observable.tags).toTry(tagSrv.getOrCreate)
      tagsToRemove = get(observable).tags.toSeq.filterNot(t => tags.contains(t.toString))
      _ <- tagsToAdd.toTry(observableTagSrv.create(ObservableTag(), observable, _))
      _ = if (tags.nonEmpty) get(observable).outE[ObservableTag].filter(_.otherV.hasId(tagsToRemove.map(_._id): _*)).remove()
      _ <- get(observable).update(_.tags, tags.toSeq).getOrFail("Observable")
      _ <- auditSrv.observable.update(observable, Json.obj("tags" -> tags))
    } yield (tagsToAdd, tagsToRemove)

  override def delete(observable: Observable with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    get(observable).alert.headOption match {
      case None =>
        get(observable)
          .share
          .toIterator
          .toTry {
            case share if share.owner =>
              get(observable)
                .shares
                .toIterator
                .toTry { share =>
                  auditSrv
                    .observable
                    .delete(observable, share)
                }
                .map(_ => get(observable).remove())
            case share =>
              for {
                organisation <- organisationSrv.current.getOrFail("Organisation")
                _            <- shareSrv.unshareObservable(observable, organisation)
                _            <- auditSrv.observable.delete(observable, share)
              } yield ()
          }
          .map(_ => ())
      case Some(alert) =>
        get(observable).remove()
        auditSrv.observableInAlert.delete(observable, alert)
    }

  override def update(
      traversal: Traversal.V[Observable],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Observable], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (observableSteps, updatedFields) =>
        observableSteps
          .clone()
          .getOrFail("Observable")
          .flatMap(observable => auditSrv.observable.update(observable, updatedFields))
    }
}

object ObservableOps {

  implicit class ObservableOpsDefs(traversal: Traversal.V[Observable]) {
    def get(idOrName: EntityIdOrName): Traversal.V[Observable] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.empty)

    def filterOnType(`type`: String): Traversal.V[Observable] =
      traversal.has(_.dataType, `type`)

    def filterOnData(data: String): Traversal.V[Observable] =
      traversal.has(_.data, data)

    def filterOnAttachmentName(name: String): Traversal.V[Observable] =
      traversal.filter(_.attachments.has(_.name, name))

    def filterOnAttachmentSize(size: Long): Traversal.V[Observable] =
      traversal.filter(_.attachments.has(_.size, size))

    def filterOnAttachmentContentType(contentType: String): Traversal.V[Observable] =
      traversal.filter(_.attachments.has(_.contentType, contentType))

    def filterOnAttachmentHash(hash: String): Traversal.V[Observable] =
      traversal.filter(_.attachments.has(_.hashes, Hash(hash)))

    def filterOnAttachmentId(attachmentId: String): Traversal.V[Observable] =
      traversal.filter(_.attachments.has(_.attachmentId, attachmentId))

    def relatedTo(caseId: EntityId): Traversal.V[Observable] =
      traversal.has(_.relatedId, caseId)

    def inOrganisation(organisationId: EntityId): Traversal.V[Observable] =
      traversal.has(_.organisationIds, organisationId)

    def isIoc: Traversal.V[Observable] =
      traversal.has(_.ioc, true)

    def visible(organisationSrv: OrganisationSrv)(implicit authContext: AuthContext): Traversal.V[Observable] =
      traversal.has(_.organisationIds, organisationSrv.currentId(traversal.graph, authContext))

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Observable] =
      if (authContext.permissions.contains(permission))
        traversal.filter(_.shares.filter(_.filter(_.profile.has(_.permissions, permission))).organisation.current)
      else
        traversal.empty

    def canManage(organisationSrv: OrganisationSrv)(implicit authContext: AuthContext): Traversal.V[Observable] =
      if (authContext.isPermitted(Permissions.manageAlert))
        traversal.filter(_.or(_.alert.visible(organisationSrv), _.can(Permissions.manageObservable)))
      else
        can(Permissions.manageObservable)

    def userPermissions(implicit authContext: AuthContext): Traversal[Set[Permission], Vertex, Converter[Set[Permission], Vertex]] =
      traversal
        .share(authContext.organisation)
        .profile
        .domainMap(profile => profile.permissions & authContext.permissions)

    def organisations: Traversal.V[Organisation] =
      traversal.coalesceIdent(_.in[ShareObservable].in[OrganisationShare], _.in[AlertObservable].out[AlertOrganisation]).v[Organisation]

    def origin: Traversal.V[Organisation] = shares.has(_.owner, true).organisation

    def isShared: Traversal[Boolean, Boolean, Identity[Boolean]] =
      traversal.choose(_.inE[ShareObservable].count.is(P.gt(1)), true, false)

    def richObservable: Traversal[RichObservable, JMap[String, Any], Converter[RichObservable, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.attachments.fold)
            .by(_.reportTags.fold)
        )
        .domainMap {
          case (observable, attachment, reportTags) =>
            RichObservable(
              observable,
              attachment.headOption,
              None,
              reportTags
            )
        }

    def richObservableWithSeen(organisationSrv: OrganisationSrv)(implicit
        authContext: AuthContext
    ): Traversal[RichObservable, JMap[String, Any], Converter[RichObservable, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.attachments.fold)
            .by(_.filteredSimilar.visible(organisationSrv).limit(1).count)
            .by(_.reportTags.fold)
        )
        .domainMap {
          case (observable, attachment, count, reportTags) =>
            RichObservable(
              observable,
              attachment.headOption,
              Some(count != 0),
              reportTags
            )
        }

    def richObservableWithCustomRenderer[D, G, C <: Converter[D, G]](
        organisationSrv: OrganisationSrv,
        entityRenderer: Traversal.V[Observable] => Traversal[D, G, C]
    )(implicit authContext: AuthContext): Traversal[(RichObservable, D), JMap[String, Any], Converter[(RichObservable, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.attachments.fold)
            .by(_.filteredSimilar.visible(organisationSrv).limit(1).count)
            .by(_.reportTags.fold)
            .by(entityRenderer)
        )
        .domainMap {
          case (observable, attachment, count, reportTags, renderedEntity) =>
            RichObservable(
              observable,
              attachment.headOption,
              Some(count != 0),
              reportTags
            ) -> renderedEntity
        }

    def `case`: Traversal.V[Case] = traversal.in[ShareObservable].out[ShareCase].v[Case]

    def alert: Traversal.V[Alert] = traversal.in[AlertObservable].v[Alert]

    def tags: Traversal.V[Tag] = traversal.out[ObservableTag].v[Tag]

    def reportTags: Traversal.V[ReportTag] = traversal.out[ObservableReportTag].v[ReportTag]

    def removeTags(tags: Set[Tag with Entity]): Unit =
      if (tags.nonEmpty)
        traversal.outE[ObservableTag].filter(_.otherV.hasId(tags.map(_._id).toSeq: _*)).remove()

    def filteredSimilar: Traversal.V[Observable] =
      traversal
        .hasNot(_.ignoreSimilarity, true)
        .similar
        .hasNot(_.ignoreSimilarity, true)

    def similar: Traversal.V[Observable] = {
      val originLabel = StepLabel.v[Observable]
      traversal
        .aggregateLocal(originLabel)
        .unionFlat(
          _.out[ObservableData]
            .in[ObservableData],
          _.out[ObservableAttachment]
            .in[ObservableAttachment] // FIXME this doesn't work. Link must be done with attachmentId
        )
        .where(P.without(originLabel.name))
        .dedup
        .v[Observable]
    }

    def data: Traversal.V[Data] = traversal.out[ObservableData].v[Data]

    def attachments: Traversal.V[Attachment] = traversal.out[ObservableAttachment].v[Attachment]

    def keyValues: Traversal.V[KeyValue] = traversal.out[ObservableKeyValue].v[KeyValue]

    def observableType: Traversal.V[ObservableType] = traversal.out[ObservableObservableType].v[ObservableType]

    def typeName: Traversal[String, String, Converter[String, String]] = observableType.value(_.name)

    def shares: Traversal.V[Share] = traversal.in[ShareObservable].v[Share]

    def share(implicit authContext: AuthContext): Traversal.V[Share] = share(authContext.organisation)

    def share(organisationName: EntityIdOrName): Traversal.V[Share] =
      shares.filter(_.byOrganisation(organisationName))
  }
}

class ObservableIntegrityCheckOps @Inject() (val db: Database, val service: ObservableSrv) extends IntegrityCheckOps[Observable] {
  override def resolve(entities: Seq[Observable with Entity])(implicit graph: Graph): Try[Unit] = Success(())

  override def globalCheck(): Map[String, Long] =
    db.tryTransaction { implicit graph =>
      Try {
        val orphanIds = service.startTraversal.filterNot(_.or(_.shares, _.alert, _.in("ReportObservable")))._id.toSeq
        if (orphanIds.nonEmpty) {
          logger.warn(s"Found ${orphanIds.length} observables orphan(s) (${orphanIds.mkString(",")})")
          service.getByIds(orphanIds: _*).remove()
        }
        Map("orphans" -> orphanIds.size.toLong)
      }
    }.getOrElse(Map("globalFailure" -> 1L))
}
