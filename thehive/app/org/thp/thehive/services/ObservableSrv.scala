package org.thp.thehive.services

import java.util.{Set => JSet}

import gremlin.scala.{KeyValue => _, _}
import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.{P => JP}
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.services._
import org.thp.thehive.models._

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

@Singleton
class ObservableSrv @Inject()(
    keyValueSrv: KeyValueSrv,
    dataSrv: DataSrv,
    attachmentSrv: AttachmentSrv,
    tagSrv: TagSrv,
    caseSrv: CaseSrv,
    shareSrv: ShareSrv
)(
    implicit db: Database
) extends VertexSrv[Observable, ObservableSteps] {
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
  ): Try[RichObservable] = {
    val createdObservable = create(observable)
    observableObservableType.create(ObservableObservableType(), createdObservable, `type`)
    observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)
    for {
      tags <- addTags(createdObservable, tagNames)
      ext  <- addExtensions(createdObservable, extensions)
    } yield RichObservable(createdObservable, `type`, None, Some(attachment), tags, ext)
  }

  def create(observable: Observable, `type`: ObservableType with Entity, dataValue: String, tagNames: Set[String], extensions: Seq[KeyValue])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] = {
    val createdObservable = create(observable)
    observableObservableType.create(ObservableObservableType(), createdObservable, `type`)
    val data = dataSrv.create(Data(dataValue))
    observableDataSrv.create(ObservableData(), createdObservable, data)
    for {
      tags <- addTags(createdObservable, tagNames)
      ext  <- addExtensions(createdObservable, extensions)
    } yield RichObservable(createdObservable, `type`, Some(data), None, tags, ext)
  }

  def updateTags(observable: Observable with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val (tagsToAdd, tagsToRemove) = get(observable)
      .tags
      .toIterator
      .foldLeft((tags, Set.empty[String])) {
        case ((toAdd, toRemove), t) if toAdd.contains(t.name) => (toAdd - t.name, toRemove)
        case ((toAdd, toRemove), t)                           => (toAdd, toRemove + t.name)
      }
    tagsToAdd
      .toTry(tn => tagSrv.getOrCreate(tn).map(t => observableTagSrv.create(ObservableTag(), observable, t)))
      .map(_ => get(observable).removeTags(tagsToRemove))
  }

  def addTags(observable: Observable with Entity, tags: Set[String])(implicit graph: Graph, authContext: AuthContext): Try[Seq[Tag with Entity]] = {
    val currentTags = get(observable)
      .tags
      .toList
      .map(_.name)
      .toSet
    (tags.toSet -- currentTags)
      .toTry { tn =>
        tagSrv.getOrCreate(tn).map { t =>
          observableTagSrv.create(ObservableTag(), observable, t)
          t
        }
      }
  }

  private def addExtensions(observable: Observable with Entity, extensions: Seq[KeyValue])(
      implicit graph: Graph,
      authContext: AuthContext
  ) =
    Success(
      extensions
        .map(keyValueSrv.create)
        .map { kv =>
          observableKeyValueSrv.create(ObservableKeyValue(), observable, kv)
          kv
        }
    )

  def duplicate(richObservable: RichObservable)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] = {

    val createdObservable = create(richObservable.observable)
    observableObservableType.create(ObservableObservableType(), createdObservable, richObservable.`type`)
    richObservable.data.foreach { data =>
      observableDataSrv.create(ObservableData(), createdObservable, data)
    }
    richObservable.attachment.foreach { attachment =>
      observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)
    }
    // TODO copy or link key value ?
    Success(richObservable.copy(observable = createdObservable))
  }

  def cascadeRemove(observable: Observable with Entity)(implicit graph: Graph): Try[Unit] =
    for {
      _ <- Try(get(observable).data.remove())
      _ <- Try(get(observable).attachments.remove())
      _ <- Try(get(observable).keyValues.remove())
      r <- Try(get(observable).remove())
    } yield r
}

class ObservableSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Observable, ObservableSteps](raw) {

  def visible(implicit authContext: AuthContext): ObservableSteps = newInstance(
    raw.filter(_.inTo[ShareObservable].inTo[OrganisationShare].inTo[RoleOrganisation].inTo[UserRole].has(Key("login") of authContext.userId))
  )

  def can(permission: Permission)(implicit authContext: AuthContext): ObservableSteps =
    newInstance(
      raw.filter(
        _.inTo[ShareObservable]
          .filter(_.outTo[ShareProfile].has(Key("permissions") of permission))
          .inTo[OrganisationShare]
          .inTo[RoleOrganisation]
          .filter(_.outTo[RoleProfile].has(Key("permissions") of permission))
          .inTo[UserRole]
          .has(Key("login") of authContext.userId)
      )
    )

  def richObservable: ScalarSteps[RichObservable] =
    ScalarSteps(
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
  ): ScalarSteps[(RichObservable, A)] =
    ScalarSteps(
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

  def removeTags(tagNames: Set[String]): Unit = {
    raw.outToE[ObservableTag].where(_.otherV().has(Key[String]("name"), P.within(tagNames))).drop().iterate()
    ()
  }

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

  override def newInstance(raw: GremlinScala[Vertex]): ObservableSteps = new ObservableSteps(raw)

  def remove(): Unit = {
    raw.drop().iterate()
    ()
  }

  def data        = new DataSteps(raw.outTo[ObservableData])
  def attachments = new AttachmentSteps(raw.outTo[ObservableAttachment])
  def keyValues   = new KeyValueSteps(raw.outTo[ObservableKeyValue])
}
