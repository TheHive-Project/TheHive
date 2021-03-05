package org.thp.thehive.services

import akka.actor.ActorRef
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.{T, Vertex}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{VertexSrv, _}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.thehive.models._
import org.thp.thehive.services.DataOps._

import java.lang.{Long => JLong}
import javax.inject.{Inject, Named, Singleton}
import scala.util.{Success, Try}

@Singleton
class DataSrv @Inject() (@Named("integrity-check-actor") integrityCheckActor: ActorRef) extends VertexSrv[Data] {
  override def createEntity(e: Data)(implicit graph: Graph, authContext: AuthContext): Try[Data with Entity] =
    super.createEntity(e).map { data =>
      integrityCheckActor ! EntityAdded("Data")
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

class DataIntegrityCheckOps @Inject() (val db: Database, val service: DataSrv) extends IntegrityCheckOps[Data] {
  override def resolve(entities: Seq[Data with Entity])(implicit graph: Graph): Try[Unit] =
    entities match {
      case head :: tail =>
        tail.foreach(copyEdge(_, head))
        service.getByIds(tail.map(_._id): _*).remove()
        Success(())
      case _ => Success(())
    }

  override def globalCheck(): Map[String, Long] =
    db.tryTransaction { implicit graph =>
      Try {
        val orphans = service.startTraversal.filterNot(_.inE[ObservableData])._id.toSeq
        if (orphans.nonEmpty) {
          service.getByIds(orphans: _*).remove()
          Map("orphan" -> orphans.size.toLong)
        } else Map.empty[String, Long]
      }
    }.getOrElse(Map("globalFailure" -> 1L))
}
