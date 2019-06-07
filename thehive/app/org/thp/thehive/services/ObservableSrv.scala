package org.thp.thehive.services

import gremlin.scala.{KeyValue ⇒ _, _}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers.FFile
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity, ScalarSteps}
import org.thp.scalligraph.services._
import org.thp.thehive.models._

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

@Singleton
class ObservableSrv @Inject()(keyValueSrv: KeyValueSrv, dataSrv: DataSrv, attachmentSrv: AttachmentSrv, caseSrv: CaseSrv, shareSrv: ShareSrv)(
    implicit db: Database
) extends VertexSrv[Observable, ObservableSteps] {
  val observableKeyValueSrv   = new EdgeSrv[ObservableKeyValue, Observable, KeyValue]
  val observableDataSrv       = new EdgeSrv[ObservableData, Observable, Data]
  val observableAttachmentSrv = new EdgeSrv[ObservableAttachment, Observable, Attachment]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ObservableSteps = new ObservableSteps(raw)

  def create(observable: Observable, dataOrFile: Either[Data, FFile], extensions: Seq[KeyValue], `case`: Case with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[RichObservable] = {
    val createdObservable = create(observable)
    (dataOrFile match {
      case Left(data0) ⇒
        observableDataSrv.create(ObservableData(), createdObservable, dataSrv.create(data0))
        Success(Some(dataSrv.create(data0)) → None)
      case Right(file) ⇒
        attachmentSrv.create(file).map { attachment ⇒
          observableAttachmentSrv.create(ObservableAttachment(), createdObservable, attachment)
          None → Some(attachment)
        }
    }).flatMap {
      case (data, attachment) ⇒
        extensions
          .map(keyValueSrv.create)
          .map(kv ⇒ observableKeyValueSrv.create(ObservableKeyValue(), createdObservable, kv))

        for {
          share ← caseSrv
            .initSteps
            .getOrganisationShare(`case`._id)
            .getOrFail()
          _ = shareSrv.shareObservableSrv.create(ShareObservable(), share, createdObservable)
        } yield RichObservable(createdObservable, data, attachment, extensions)
    }
  }
}

class ObservableSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Observable, ObservableSteps](raw) {

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
            .and(By(__[Vertex].outTo[ObservableData].fold.traversal))
            .and(By(__[Vertex].outTo[ObservableAttachment].fold.traversal))
            .and(By(__[Vertex].outTo[ObservableKeyValue].fold.traversal))
        )
        .map {
          case (observable, data, attachment, extensions) ⇒
            RichObservable(
              observable.as[Observable],
              atMostOneOf[Vertex](data).map(_.as[Data]),
              atMostOneOf[Vertex](attachment).map(_.as[Attachment]),
              extensions.asScala.map(_.as[KeyValue])
            )
        }
    )

  def cases: CaseSteps = new CaseSteps(raw.inTo[ShareObservable].outTo[ShareCase])

  override def newInstance(raw: GremlinScala[Vertex]): ObservableSteps = new ObservableSteps(raw)

  def similar(id: String): ObservableSteps = newInstance(
    raw
      .unionFlat(
        _.outTo[ObservableData]
          .inTo[ObservableData]
          .hasNot(Key("_id") of id),
        _.outTo[ObservableAttachment]
          .inTo[ObservableAttachment]
          .hasNot(Key("_id") of id)
      )
      .dedup
  )
}
