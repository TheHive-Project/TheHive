package org.thp.thehive.services

import akka.actor.typed.ActorRef
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.thehive.models._
import org.thp.thehive.services.DataOps._

import java.lang.{Long => JLong}
import javax.inject.{Inject, Provider, Singleton}
import scala.util.{Success, Try}

@Singleton
class DataSrv @Inject() (integrityCheckActorProvider: Provider[ActorRef[IntegrityCheck.Request]]) extends VertexSrv[Data] {
  lazy val integrityCheckActor: ActorRef[IntegrityCheck.Request] = integrityCheckActorProvider.get

  override def createEntity(e: Data)(implicit graph: Graph, authContext: AuthContext): Try[Data with Entity] =
    super.createEntity(e).map { data =>
      integrityCheckActor ! IntegrityCheck.EntityAdded("Data")
      data
    }

  def create(e: Data)(implicit graph: Graph, authContext: AuthContext): Try[Data with Entity] =
    startTraversal
      .getByData(e.data)
      .headOption
      .fold(createEntity(e))(Success(_))

  override def exists(e: Data)(implicit graph: Graph): Boolean = startTraversal.getByData(e.data).exists

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[Data] =
    startTraversal.getByData(name)
}

object DataOps {

  implicit class DataOpsDefs(traversal: Traversal.V[Data]) {
    def observables: Traversal.V[Observable] = traversal.in[ObservableData].v[Observable]

    def notShared(caseId: String): Traversal.V[Data] =
      traversal.filter(
        _.in[ObservableData]
          .in[ShareObservable]
          .out[ShareCase]
          .has(T.id, P.neq(caseId))
          .count
          .is(P.eq(0))
      )

    def getByData(data: String): Traversal.V[Data] = traversal.has(_.data, data)

    def useCount: Traversal[Long, JLong, Converter[Long, JLong]] = traversal.in[ObservableData].count
  }

}

class DataIntegrityCheck @Inject() (val db: Database, val service: DataSrv) extends DedupCheck[Data] with GlobalCheck[Data] {

  override def extraFilter(traversal: Traversal.V[Data]): Traversal.V[Data] = traversal.filterNot(_.inE[ObservableData])
  override def globalCheck(traversal: Traversal.V[Data])(implicit graph: Graph): Map[String, Long] =
    Map("orphan" -> traversal.sideEffect(_.drop()).getCount)
}
