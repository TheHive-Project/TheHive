package org.thp.thehive.services
import java.util.{List ⇒ JList}

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
  val observableCaseSrv       = new EdgeSrv[ObservableCase, Observable, Case]

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
    observableCaseSrv.create(ObservableCase(), createdObservable, `case`)
    RichObservable(createdObservable, data, attachment, extensions)
  }
}

class ObservableSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Observable, ObservableSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ObservableSteps = new ObservableSteps(raw)

  def richObservable: ScalarSteps[RichObservable] =
    ScalarSteps(
      raw
        .project[Any]("observable", "data", "attachment", "extensions")
        .by()
        .by(__[Vertex].outTo[ObservableData].fold.traversal)
        .by(__[Vertex].outTo[ObservableAttachment].fold.traversal)
        .by(__[Vertex].outTo[ObservableKeyValue].fold.traversal)
        .map {
          case ValueMap(m) ⇒
            RichObservable(
              m.get[Vertex]("observable").as[Observable],
              atMostOneOf[Vertex](m.get[JList[Vertex]]("data")).map(_.as[Data]),
              atMostOneOf[Vertex](m.get[JList[Vertex]]("attachment")).map(_.as[Attachment]),
              m.get[JList[Vertex]]("extensions").asScala.map(_.as[KeyValue]).toSeq
            )
        }
    )
}
