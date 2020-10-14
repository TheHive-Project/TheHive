package org.thp.thehive.services

import java.util.{Map => JMap}

import javax.inject.{Inject, Named, Provider, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.{P => JP}
import org.apache.tinkerpop.gremlin.structure.{Graph, Vertex}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, StepLabel, Traversal}
import org.thp.scalligraph.utils.Hash
import org.thp.scalligraph.{EntityIdOrName, RichSeq}
import org.thp.thehive.models._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.ShareOps._
import play.api.libs.json.JsObject

import scala.util.Try

@Singleton
class ObservableSrv @Inject() (
    keyValueSrv: KeyValueSrv,
    dataSrv: DataSrv,
    attachmentSrv: AttachmentSrv,
    tagSrv: TagSrv,
    caseSrvProvider: Provider[CaseSrv],
    auditSrv: AuditSrv,
    alertSrvProvider: Provider[AlertSrv]
)(implicit
    @Named("with-thehive-schema") db: Database
) extends VertexSrv[Observable] {
  lazy val caseSrv: CaseSrv    = caseSrvProvider.get
  lazy val alertSrv: AlertSrv  = alertSrvProvider.get
  val observableKeyValueSrv    = new EdgeSrv[ObservableKeyValue, Observable, KeyValue]
  val observableDataSrv        = new EdgeSrv[ObservableData, Observable, Data]
  val observableObservableType = new EdgeSrv[ObservableObservableType, Observable, ObservableType]
  val observableAttachmentSrv  = new EdgeSrv[ObservableAttachment, Observable, Attachment]
  val observableTagSrv         = new EdgeSrv[ObservableTag, Observable, Tag]

  def create(observable: Observable, `type`: ObservableType with Entity, file: FFile, tagNames: Set[String], extensions: Seq[KeyValue])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    attachmentSrv.create(file).flatMap { attachment =>
      create(observable, `type`, attachment, tagNames, extensions)
    }

  def create(
      observable: Observable,
      `type`: ObservableType with Entity,
      attachment: Attachment with Entity,
      tagNames: Set[String],
      extensions: Seq[KeyValue]
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    tagNames.toTry(tagSrv.getOrCreate).flatMap(tags => create(observable, `type`, attachment, tags, extensions))

  def create(
      observable: Observable,
      `type`: ObservableType with Entity,
      attachment: Attachment with Entity,
      tags: Seq[Tag with Entity],
      extensions: Seq[KeyValue]
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- createEntity(observable)
      _                 <- observableObservableType.create(ObservableObservableType(), createdObservable, `type`)
      _                 <- observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)
      _                 <- tags.toTry(observableTagSrv.create(ObservableTag(), createdObservable, _))
      ext               <- addExtensions(createdObservable, extensions)
    } yield RichObservable(createdObservable, `type`, None, Some(attachment), tags, None, ext, Nil)

  def create(observable: Observable, `type`: ObservableType with Entity, dataValue: String, tagNames: Set[String], extensions: Seq[KeyValue])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      tags           <- tagNames.toTry(tagSrv.getOrCreate)
      data           <- dataSrv.create(Data(dataValue))
      richObservable <- create(observable, `type`, data, tags, extensions)
    } yield richObservable

  def create(
      observable: Observable,
      `type`: ObservableType with Entity,
      data: Data with Entity,
      tags: Seq[Tag with Entity],
      extensions: Seq[KeyValue]
  )(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- createEntity(observable)
      _                 <- observableObservableType.create(ObservableObservableType(), createdObservable, `type`)
      _                 <- observableDataSrv.create(ObservableData(), createdObservable, data)
      _                 <- tags.toTry(observableTagSrv.create(ObservableTag(), createdObservable, _))
      ext               <- addExtensions(createdObservable, extensions)
    } yield RichObservable(createdObservable, `type`, Some(data), None, tags, None, ext, Nil)

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

  private def addExtensions(observable: Observable with Entity, extensions: Seq[KeyValue])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Seq[KeyValue with Entity]] =
    for {
      keyValues <- extensions.toTry(keyValueSrv.create)
      _         <- keyValues.toTry(kv => observableKeyValueSrv.create(ObservableKeyValue(), observable, kv))
    } yield keyValues

  def updateTagNames(observable: Observable with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    tags.toTry(tagSrv.getOrCreate).flatMap(t => updateTags(observable, t.toSet))

  def updateTags(observable: Observable with Entity, tags: Set[Tag with Entity])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(observable)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[Tag with Entity])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t) => (toAdd - t, toRemove)
        case ((toAdd, toRemove), t)                      => (toAdd, toRemove + t)
      }
    for {
      _ <- tagsToAdd.toTry(observableTagSrv.create(ObservableTag(), observable, _))
      _ = get(observable).removeTags(tagsToRemove)
      //      _ <- auditSrv.observable.update(observable, Json.obj("tags" -> tags)) TODO add context (case or alert ?)
    } yield ()
  }

  def duplicate(richObservable: RichObservable)(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- createEntity(richObservable.observable)
      _                 <- observableObservableType.create(ObservableObservableType(), createdObservable, richObservable.`type`)
      _                 <- richObservable.data.map(data => observableDataSrv.create(ObservableData(), createdObservable, data)).flip
      _                 <- richObservable.attachment.map(attachment => observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)).flip
      _                 <- richObservable.tags.toTry(tag => observableTagSrv.create(ObservableTag(), createdObservable, tag))
      // TODO copy or link key value ?
    } yield richObservable.copy(observable = createdObservable)

  def remove(observable: Observable with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    get(observable).alert.headOption match {
      case None =>
        get(observable)
          .shares
          .toIterator
          .toTry { share =>
            auditSrv
              .observable
              .delete(observable, share)
              .map(_ => get(observable).remove())
          }
          .map(_ => ())
      case Some(alert) => alertSrv.removeObservable(alert, observable)
    }

  override def update(
      traversal: Traversal.V[Observable],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Observable], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (observableSteps, updatedFields) =>
        for {
          observable <- observableSteps.getOrFail("Observable")
          _          <- auditSrv.observable.update(observable, updatedFields)
        } yield ()
    }
}

object ObservableOps {

  implicit class ObservableOpsDefs(traversal: Traversal.V[Observable]) {
    def get(idOrName: EntityIdOrName): Traversal.V[Observable] =
      idOrName.fold(traversal.getByIds(_), _ => traversal.limit(0))

    def filterOnType(`type`: String): Traversal.V[Observable] =
      traversal.filter(_.observableType.has(_.name, `type`))

    def filterOnData(data: String): Traversal.V[Observable] =
      traversal.filter(_.data.has(_.data, data))

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

    def isIoc: Traversal.V[Observable] =
      traversal.has(_.ioc, true)

    def visible(implicit authContext: AuthContext): Traversal.V[Observable] =
      traversal.filter(_.organisations.get(authContext.organisation))

    def can(permission: Permission)(implicit authContext: AuthContext): Traversal.V[Observable] =
      if (authContext.permissions.contains(permission))
        traversal.filter(_.shares.filter(_.filter(_.profile.has(_.permissions, permission))).organisation.current)
      else
        traversal.limit(0)

    def userPermissions(implicit authContext: AuthContext): Traversal[Set[Permission], Vertex, Converter[Set[Permission], Vertex]] =
      traversal
        .share(authContext.organisation)
        .profile
        .domainMap(profile => profile.permissions & authContext.permissions)

    def organisations: Traversal.V[Organisation] = traversal.in[ShareObservable].in[OrganisationShare].v[Organisation]

    def origin: Traversal.V[Organisation] = shares.has(_.owner, true).organisation

    def richObservable: Traversal[RichObservable, JMap[String, Any], Converter[RichObservable, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.observableType.fold)
            .by(_.data.fold)
            .by(_.attachments.fold)
            .by(_.tags.fold)
            .by(_.keyValues.fold)
            .by(_.reportTags.fold)
        )
        .domainMap {
          case (observable, tpe, data, attachment, tags, extensions, reportTags) =>
            RichObservable(
              observable,
              tpe.head,
              data.headOption,
              attachment.headOption,
              tags,
              None,
              extensions,
              reportTags
            )
        }

    def richObservableWithSeen(implicit
        authContext: AuthContext
    ): Traversal[RichObservable, JMap[String, Any], Converter[RichObservable, JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.observableType.fold)
            .by(_.data.fold)
            .by(_.attachments.fold)
            .by(_.tags.fold)
            .by(_.similar.visible.limit(1).count)
            .by(_.keyValues.fold)
            .by(_.reportTags.fold)
        )
        .domainMap {
          case (observable, tpe, data, attachment, tags, count, extensions, reportTags) =>
            RichObservable(
              observable,
              tpe.head,
              data.headOption,
              attachment.headOption,
              tags,
              Some(count != 0),
              extensions,
              reportTags
            )
        }

    def richObservableWithCustomRenderer[D, G, C <: Converter[D, G]](
        entityRenderer: Traversal.V[Observable] => Traversal[D, G, C]
    )(implicit authContext: AuthContext): Traversal[(RichObservable, D), JMap[String, Any], Converter[(RichObservable, D), JMap[String, Any]]] =
      traversal
        .project(
          _.by
            .by(_.observableType.fold)
            .by(_.data.fold)
            .by(_.attachments.fold)
            .by(_.tags.fold)
            .by(_.similar.visible.limit(1).count)
            .by(_.keyValues.fold)
            .by(_.reportTags.fold)
            .by(entityRenderer)
        )
        .domainMap {
          case (observable, tpe, data, attachment, tags, count, extensions, reportTags, renderedEntity) =>
            RichObservable(
              observable,
              tpe.head,
              data.headOption,
              attachment.headOption,
              tags,
              Some(count != 0),
              extensions,
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

    def similar: Traversal.V[Observable] = {
      val originLabel = StepLabel.v[Observable]
      traversal
        .aggregateLocal(originLabel)
        .unionFlat(
          _.out[ObservableData]
            .in[ObservableData],
          _.out[ObservableAttachment]
            .in[ObservableAttachment]
        )
        .where(JP.without(originLabel.name))
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
