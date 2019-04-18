package org.thp.thehive.services

import scala.collection.JavaConverters._

import gremlin.scala.{KeyValue ⇒ _, _}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class ObservableSrv @Inject()(keyValueSrv: KeyValueSrv, dataSrv: DataSrv, attachmentSrv: AttachmentSrv)(implicit db: Database)
    extends VertexSrv[Observable, ObservableSteps] {
  val observableKeyValueSrv   = new EdgeSrv[ObservableKeyValue, Observable, KeyValue]
  val observableDataSrv       = new EdgeSrv[ObservableData, Observable, Data]
  val observableAttachmentSrv = new EdgeSrv[ObservableAttachment, Observable, Attachment]
  val caseObservableSrv       = new EdgeSrv[CaseObservable, Case, Observable]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ObservableSteps = new ObservableSteps(raw)

  def create(observable: Observable, dataOrFile: Either[Data, FFile], extensions: Seq[KeyValue], `case`: Case with Entity)(
      implicit graph: Graph,
      authContext: AuthContext): RichObservable = {
    val createdObservable = create(observable)
    val (data, attachment) = dataOrFile match {
      case Left(data0) ⇒
        observableDataSrv.create(ObservableData(), createdObservable, dataSrv.create(data0))
        Some(dataSrv.create(data0)) → None
      case Right(file) ⇒
        observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachmentSrv.create(file))
        None → Some(attachmentSrv.create(file))
    }
    extensions
      .map(keyValueSrv.create)
      .map(kv ⇒ observableKeyValueSrv.create(ObservableKeyValue(), createdObservable, kv))
    caseObservableSrv.create(CaseObservable(), `case`, createdObservable)
    RichObservable(createdObservable, data, attachment, extensions)
  }
}

class ObservableSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Observable, ObservableSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ObservableSteps = new ObservableSteps(raw)

  def richObservable: ScalarSteps[RichObservable] =
    ScalarSteps(
      raw
        .project(
          _.apply(By[Vertex]())
            .and(By(__[Vertex].outTo[ObservableData].fold.traversal))
            .and(By(__[Vertex].outTo[ObservableAttachment].fold.traversal))
            .and(By(__[Vertex].outTo[ObservableKeyValue].fold.traversal)))
        .map {
          case (observable, data, attachment, extensions) ⇒
            RichObservable(
              observable.as[Observable],
              atMostOneOf[Vertex](data).map(_.as[Data]),
              atMostOneOf[Vertex](attachment).map(_.as[Attachment]),
              extensions.asScala.map(_.as[KeyValue]).toSeq
            )
        }
    )

  def cases: CaseSteps = new CaseSteps(raw.inTo[CaseObservable])
}
