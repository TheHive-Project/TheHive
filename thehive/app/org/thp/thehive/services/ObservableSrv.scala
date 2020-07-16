package org.thp.thehive.services

import java.util.{Set => JSet}

import gremlin.scala.{KeyValue => _, _}
import javax.inject.{Inject, Named, Provider, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.{P => JP}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.{Traversal, TraversalLike, VertexSteps}
import org.thp.scalligraph.{EntitySteps, RichSeq}
import org.thp.thehive.models._
import play.api.libs.json.JsObject

import scala.collection.JavaConverters._
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
)(
    implicit @Named("with-thehive-schema") db: Database
) extends VertexSrv[Observable, ObservableSteps] {
  lazy val caseSrv: CaseSrv    = caseSrvProvider.get
  lazy val alertSrv: AlertSrv  = alertSrvProvider.get
  val observableKeyValueSrv    = new EdgeSrv[ObservableKeyValue, Observable, KeyValue]
  val observableDataSrv        = new EdgeSrv[ObservableData, Observable, Data]
  val observableObservableType = new EdgeSrv[ObservableObservableType, Observable, ObservableType]
  val observableAttachmentSrv  = new EdgeSrv[ObservableAttachment, Observable, Attachment]
  val observableTagSrv         = new EdgeSrv[ObservableTag, Observable, Tag]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ObservableSteps = new ObservableSteps(raw)

  def create(observable: Observable, `type`: ObservableType with Entity, file: FFile, tagNames: Set[String], extensions: Seq[KeyValue])(
      implicit graph: Graph,
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
  )(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    tagNames.toTry(tagSrv.getOrCreate).flatMap(tags => create(observable, `type`, attachment, tags, extensions))

  def create(
      observable: Observable,
      `type`: ObservableType with Entity,
      attachment: Attachment with Entity,
      tags: Seq[Tag with Entity],
      extensions: Seq[KeyValue]
  )(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- createEntity(observable)
      _                 <- observableObservableType.create(ObservableObservableType(), createdObservable, `type`)
      _                 <- observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)
      _                 <- tags.toTry(observableTagSrv.create(ObservableTag(), createdObservable, _))
      ext               <- addExtensions(createdObservable, extensions)
    } yield RichObservable(createdObservable, `type`, None, Some(attachment), tags, None, ext, Nil)

  def create(observable: Observable, `type`: ObservableType with Entity, dataValue: String, tagNames: Set[String], extensions: Seq[KeyValue])(
      implicit graph: Graph,
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
  )(
      implicit graph: Graph,
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
      .toList
      .map(_.toString)
      .toSet
    for {
      createdTags <- (tags -- currentTags).filterNot(_.isEmpty).toTry(tagSrv.getOrCreate)
      _           <- createdTags.toTry(observableTagSrv.create(ObservableTag(), observable, _))
//      _           <- auditSrv.observable.update(observable, Json.obj("tags" -> (currentTags ++ tags)))
    } yield createdTags
  }

  private def addExtensions(observable: Observable with Entity, extensions: Seq[KeyValue])(
      implicit graph: Graph,
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

  def duplicate(richObservable: RichObservable)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- createEntity(richObservable.observable)
      _                 <- observableObservableType.create(ObservableObservableType(), createdObservable, richObservable.`type`)
      _                 <- richObservable.data.map(data => observableDataSrv.create(ObservableData(), createdObservable, data)).flip
      _                 <- richObservable.attachment.map(attachment => observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)).flip
      // TODO copy or link key value ?
    } yield richObservable.copy(observable = createdObservable)

  def remove(observable: Observable with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    get(observable).alert.headOption() match {
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
      steps: ObservableSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(ObservableSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (observableSteps, updatedFields) =>
        for {
          observable <- observableSteps.getOrFail()
          _          <- auditSrv.observable.update(observable, updatedFields)
        } yield ()
    }
}

@EntitySteps[Observable]
class ObservableSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph)
    extends VertexSteps[Observable](raw) {

  def filterOnType(`type`: String): ObservableSteps =
    this.filter(_.outTo[ObservableObservableType].has("name", `type`))

  def filterOnData(data: String): ObservableSteps =
    this.filter(_.outTo[ObservableData].has("data", data))

  def filterOnAttachmentName(name: String): ObservableSteps =
    this.filter(_.outTo[ObservableAttachment].has("name", name))

  def filterOnAttachmentSize(size: Long): ObservableSteps =
    this.filter(_.outTo[ObservableAttachment].has("size", size))

  def filterOnAttachmentContentType(contentType: String): ObservableSteps =
    this.filter(_.outTo[ObservableAttachment].has("contentType", contentType))

  def filterOnAttachmentHash(hash: String): ObservableSteps =
    this.filter(_.outTo[ObservableAttachment].has("hashes", hash))

  def visible(implicit authContext: AuthContext): ObservableSteps =
    this.filter(_.inTo[ShareObservable].inTo[OrganisationShare].has("name", authContext.organisation))

  def can(permission: Permission)(implicit authContext: AuthContext): ObservableSteps =
    if (authContext.permissions.contains(permission))
      this.filter(
        _.inTo[ShareObservable]
          .filter(_.outTo[ShareProfile].has("permissions", permission))
          .inTo[OrganisationShare]
          .has("name", authContext.organisation)
      )
    else
      this.limit(0)

  def userPermissions(implicit authContext: AuthContext): Traversal[Set[Permission], Set[Permission]] =
    this
      .share(authContext.organisation)
      .profile
      .map(profile => profile.permissions & authContext.permissions)

  def organisations = new OrganisationSteps(raw.inTo[ShareObservable].inTo[OrganisationShare])

  def origin: OrganisationSteps = new OrganisationSteps(raw.inTo[ShareObservable].has(Key("owner") of true).inTo[OrganisationShare])

  override def newInstance(): ObservableSteps = new ObservableSteps(raw.clone())

  def richObservable: Traversal[RichObservable, RichObservable] =
    Traversal(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[ObservableObservableType].fold))
            .and(By(__[Vertex].outTo[ObservableData].fold))
            .and(By(__[Vertex].outTo[ObservableAttachment].fold))
            .and(By(__[Vertex].outTo[ObservableTag].fold))
            .and(By(__[Vertex].outTo[ObservableKeyValue].fold))
            .and(By(__[Vertex].outTo[ObservableReportTag].fold))
        )
        .map {
          case (observable, tpe, data, attachment, tags, extensions, reportTags) =>
            RichObservable(
              observable.as[Observable],
              onlyOneOf[Vertex](tpe).as[ObservableType],
              atMostOneOf[Vertex](data).map(_.as[Data]),
              atMostOneOf[Vertex](attachment).map(_.as[Attachment]),
              tags.asScala.map(_.as[Tag]),
              None,
              extensions.asScala.map(_.as[KeyValue]),
              reportTags.asScala.map(_.as[ReportTag])
            )
        }
    )

  def richObservableWithSeen(implicit authContext: AuthContext): Traversal[RichObservable, RichObservable] =
    Traversal(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[ObservableObservableType].fold))
            .and(By(__[Vertex].outTo[ObservableData].fold))
            .and(By(__[Vertex].outTo[ObservableAttachment].fold))
            .and(By(__[Vertex].outTo[ObservableTag].fold))
            .and(By(new ObservableSteps(__[Vertex]).similar.visible.raw.limit(1).count))
            .and(By(__[Vertex].outTo[ObservableKeyValue].fold))
            .and(By(__[Vertex].outTo[ObservableReportTag].fold))
        )
        .map {
          case (observable, tpe, data, attachment, tags, count, extensions, reportTags) =>
            RichObservable(
              observable.as[Observable],
              onlyOneOf[Vertex](tpe).as[ObservableType],
              atMostOneOf[Vertex](data).map(_.as[Data]),
              atMostOneOf[Vertex](attachment).map(_.as[Attachment]),
              tags.asScala.map(_.as[Tag]),
              Some(count != 0),
              extensions.asScala.map(_.as[KeyValue]),
              reportTags.asScala.map(_.as[ReportTag])
            )
        }
    )

  def richObservableWithCustomRenderer[A](
      entityRenderer: ObservableSteps => TraversalLike[_, A]
  ): Traversal[(RichObservable, A), (RichObservable, A)] =
    Traversal(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[ObservableObservableType].fold))
            .and(By(__[Vertex].outTo[ObservableData].fold))
            .and(By(__[Vertex].outTo[ObservableAttachment].fold))
            .and(By(__[Vertex].outTo[ObservableTag].fold))
            .and(By(__[Vertex].outTo[ObservableKeyValue].fold))
            .and(By(__[Vertex].outTo[ObservableReportTag].fold))
            .and(By(entityRenderer(newInstance(__[Vertex])).raw))
        )
        .map {
          case (observable, tpe, data, attachment, tags, extensions, reportTags, renderedEntity) =>
            RichObservable(
              observable.as[Observable],
              onlyOneOf[Vertex](tpe).as[ObservableType],
              atMostOneOf[Vertex](data).map(_.as[Data]),
              atMostOneOf[Vertex](attachment).map(_.as[Attachment]),
              tags.asScala.map(_.as[Tag]),
              None,
              extensions.asScala.map(_.as[KeyValue]),
              reportTags.asScala.map(_.as[ReportTag])
            ) -> renderedEntity
        }
    )

  def `case`: CaseSteps = new CaseSteps(raw.inTo[ShareObservable].outTo[ShareCase])

  def alert: AlertSteps = new AlertSteps(raw.inTo[AlertObservable])

  def tags: TagSteps = new TagSteps(raw.outTo[ObservableTag])

  def reportTags: ReportTagSteps = new ReportTagSteps(raw.outTo[ObservableReportTag])

  def removeTags(tags: Set[Tag with Entity]): Unit =
    if (tags.nonEmpty)
      this.outToE[ObservableTag].filter(_.otherV().hasId(tags.map(_._id).toSeq: _*)).remove()

  def similar: ObservableSteps = {
    val originLabel = StepLabel[JSet[Vertex]]()
    newInstance(
      raw
        .aggregate(originLabel)
        .unionFlat(
          _.outTo[ObservableData]
            .inTo[ObservableData],
          _.outTo[ObservableAttachment]
            .inTo[ObservableAttachment]
        )
        .where(JP.without(originLabel.name))
        .dedup
    )
  }

  override def newInstance(newRaw: GremlinScala[Vertex]): ObservableSteps = new ObservableSteps(newRaw)

  def data           = new DataSteps(raw.outTo[ObservableData])
  def attachments    = new AttachmentSteps(raw.outTo[ObservableAttachment])
  def keyValues      = new KeyValueSteps(raw.outTo[ObservableKeyValue])
  def observableType = new ObservableTypeSteps(raw.outTo[ObservableObservableType])

  def shares: ShareSteps = new ShareSteps(raw.inTo[ShareObservable])

  def share(implicit authContext: AuthContext): ShareSteps = share(authContext.organisation)

  def share(organistionName: String): ShareSteps =
    new ShareSteps(
      raw
        .inTo[ShareObservable]
        .filter(_.inTo[OrganisationShare].has(Key("name") of organistionName))
    )
}
