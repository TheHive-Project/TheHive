package org.thp.thehive.services

import java.lang.{Long => JLong}
import java.util.{Set => JSet}

import scala.collection.JavaConverters._
import scala.util.Try

import play.api.libs.json.JsObject

import gremlin.scala.{KeyValue => _, _}
import javax.inject.{Inject, Provider, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.{P => JP}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.{Traversal, VertexSteps}
import org.thp.scalligraph.{EntitySteps, RichSeq}
import org.thp.thehive.models._

@Singleton
class ObservableSrv @Inject()(
    keyValueSrv: KeyValueSrv,
    dataSrv: DataSrv,
    attachmentSrv: AttachmentSrv,
    tagSrv: TagSrv,
    caseSrvProvider: Provider[CaseSrv],
    shareSrv: ShareSrv,
    auditSrv: AuditSrv
)(
    implicit db: Database
) extends VertexSrv[Observable, ObservableSteps] {
  lazy val caseSrv: CaseSrv    = caseSrvProvider.get
  val observableKeyValueSrv    = new EdgeSrv[ObservableKeyValue, Observable, KeyValue]
  val observableDataSrv        = new EdgeSrv[ObservableData, Observable, Data]
  val observableObservableType = new EdgeSrv[ObservableObservableType, Observable, ObservableType]
  val observableAttachmentSrv  = new EdgeSrv[ObservableAttachment, Observable, Attachment]
  val alertObservableSrv       = new EdgeSrv[AlertObservable, Alert, Observable]
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
    for {
      createdObservable <- createEntity(observable)
      _                 <- observableObservableType.create(ObservableObservableType(), createdObservable, `type`)
      _                 <- observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)
      tags              <- addTags(createdObservable, tagNames)
      ext               <- addExtensions(createdObservable, extensions)
    } yield RichObservable(createdObservable, `type`, None, Some(attachment), tags, ext)

  def create(observable: Observable, `type`: ObservableType with Entity, dataValue: String, tagNames: Set[String], extensions: Seq[KeyValue])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] =
    for {
      createdObservable <- createEntity(observable)
      _                 <- observableObservableType.create(ObservableObservableType(), createdObservable, `type`)
      data              <- dataSrv.create(Data(dataValue))
      _                 <- observableDataSrv.create(ObservableData(), createdObservable, data)
      tags              <- addTags(createdObservable, tagNames)
      ext               <- addExtensions(createdObservable, extensions)
    } yield RichObservable(createdObservable, `type`, Some(data), None, tags, ext)

  def addTags(observable: Observable with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Seq[Tag with Entity]] = {
    val currentTags = get(observable)
      .tags
      .toList
      .map(_.toString)
      .toSet
    for {
      createdTags <- (tags -- currentTags).toTry(tagSrv.getOrCreate)
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

  def updateTagNames(observable: Observable with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    tags.toTry(tagSrv.getOrCreate).flatMap(t => updateTags(observable, t.toSet))

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

  def cascadeRemove(observable: Observable with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(observable).data.filter(_.useCount.filter(_.is(P.eq[JLong](0L)))).remove()
    get(observable).attachments.remove()
    get(observable).keyValues.remove()
    val ctx = get(observable).`case`.getOrFail().orElse(get(observable).alert.getOrFail())
    get(observable).remove()
    ctx.flatMap {
      case case0: Case with Entity  => auditSrv.observable.delete(observable, Some(case0))
      case alert: Alert with Entity => auditSrv.observableInAlert.delete(observable, Some(alert))
      case _                        => auditSrv.observable.delete(observable, None)
    }
  }

  override def update(
      steps: ObservableSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(ObservableSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (observableSteps, updatedFields) =>
        for {
          c <- observableSteps.newInstance().`case`.getOrFail()
          o <- observableSteps.getOrFail()
          _ <- auditSrv.observable.update(o, c, updatedFields)
        } yield ()
    }
}

@EntitySteps[Observable]
class ObservableSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Observable](raw) {

  def filterOnType(`type`: String): ObservableSteps =
    this.filter(_.outTo[ObservableObservableType].has(Key("name"), P.eq(`type`)))

  def filterOnData(data: String): ObservableSteps =
    this.filter(_.outTo[ObservableData].has(Key("data"), P.eq(data)))

  def filterOnAttachmentName(name: String): ObservableSteps =
    this.filter(_.outTo[ObservableAttachment].has(Key("name"), P.eq(name)))

  def filterOnAttachmentSize(size: Long): ObservableSteps =
    this.filter(_.outTo[ObservableAttachment].has(Key("size"), P.eq(size)))

  def filterOnAttachmentContentType(contentType: String): ObservableSteps =
    this.filter(_.outTo[ObservableAttachment].has(Key("contentType"), P.eq(contentType)))

  def filterOnAttachmentHash(hash: String): ObservableSteps =
    this.filter(_.outTo[ObservableAttachment].has(Key("hashes"), P.eq(hash)))

  def visible(implicit authContext: AuthContext): ObservableSteps =
    this.filter(_.inTo[ShareObservable].inTo[OrganisationShare].inTo[RoleOrganisation].inTo[UserRole].has(Key("login"), P.eq(authContext.userId)))

  def can(permission: Permission)(implicit authContext: AuthContext): ObservableSteps =
    this.filter(
      _.inTo[ShareObservable]
        .filter(_.outTo[ShareProfile].has(Key("permissions"), P.eq(permission)))
        .inTo[OrganisationShare]
        .inTo[RoleOrganisation]
        .filter(_.outTo[RoleProfile].has(Key("permissions"), P.eq(permission)))
        .inTo[UserRole]
        .has(Key("login"), P.eq(authContext.userId))
    )

  override def newInstance(newRaw: GremlinScala[Vertex]): ObservableSteps = new ObservableSteps(newRaw)
  override def newInstance(): ObservableSteps                             = new ObservableSteps(raw.clone())

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
        )
        .map {
          case (observable, tpe, data, attachment, tags, extensions) =>
            RichObservable(
              observable.as[Observable],
              onlyOneOf[Vertex](tpe).as[ObservableType],
              atMostOneOf[Vertex](data).map(_.as[Data]),
              atMostOneOf[Vertex](attachment).map(_.as[Attachment]),
              tags.asScala.map(_.as[Tag]).toSeq,
              extensions.asScala.map(_.as[KeyValue]).toSeq
            )
        }
    )

  def richObservableWithCustomRenderer[A](
      entityRenderer: GremlinScala[Vertex] => GremlinScala[A]
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
            .and(By(entityRenderer(__[Vertex])))
        )
        .map {
          case (observable, tpe, data, attachment, tags, extensions, renderedEntity) =>
            RichObservable(
              observable.as[Observable],
              onlyOneOf[Vertex](tpe).as[ObservableType],
              atMostOneOf[Vertex](data).map(_.as[Data]),
              atMostOneOf[Vertex](attachment).map(_.as[Attachment]),
              tags.asScala.map(_.as[Tag]).toSeq,
              extensions.asScala.map(_.as[KeyValue]).toSeq
            ) -> renderedEntity
        }
    )

  def `case`: CaseSteps = new CaseSteps(raw.inTo[ShareObservable].outTo[ShareCase])

  def alert: AlertSteps = new AlertSteps(raw.inTo[AlertObservable])

  def tags: TagSteps = new TagSteps(raw.outTo[ObservableTag])

  def removeTags(tags: Set[Tag with Entity]): Unit =
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

  def data           = new DataSteps(raw.outTo[ObservableData])
  def attachments    = new AttachmentSteps(raw.outTo[ObservableAttachment])
  def keyValues      = new KeyValueSteps(raw.outTo[ObservableKeyValue])
  def observableType = new ObservableTypeSteps(raw.outTo[ObservableObservableType])
}
