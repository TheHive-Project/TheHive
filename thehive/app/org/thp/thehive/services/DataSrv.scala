package org.thp.thehive.services

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.EntityId
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{VertexSrv, _}
import org.thp.scalligraph.traversal.{Converter, Graph, Traversal}
import org.thp.thehive.models._

import java.lang.{Long => JLong}
import scala.collection.mutable
import scala.util.{Success, Try}

class DataSrv(integrityCheckActor: => ActorRef @@ IntegrityCheckTag) extends VertexSrv[Data] with TheHiveOpsNoDeps {
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

trait DataOps { _: TheHiveOpsNoDeps =>

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

class DataIntegrityCheckOps(val db: Database, val service: DataSrv) extends IntegrityCheckOps[Data] {

  override def findDuplicates(): Seq[Seq[Data with Entity]] =
    db.roTransaction { implicit graph =>
      val map = mutable.Map.empty[String, mutable.Buffer[EntityId]]
      service
        .startTraversal
        .foreach { data =>
          map.getOrElseUpdate(data.data, mutable.Buffer.empty[EntityId]) += EntityId(data._id)
        }
      map
        .values
        .collect {
          case vertexIds if vertexIds.lengthCompare(1) > 0 => service.getByIds(vertexIds.toSeq: _*).toList
        }
        .toSeq
    }
  override def resolve(entities: Seq[Data with Entity])(implicit graph: Graph): Try[Unit] =
    entities match {
      case head :: tail =>
        tail.foreach(copyEdge(_, head))
        service.getByIds(tail.map(_._id): _*).remove()
        Success(())
      case _ => Success(())
    }

  override def globalCheck(): Map[String, Int] =
    db.tryTransaction { implicit graph =>
      Try {
        val orphans = service.startTraversal.filterNot(_.inE[ObservableData])._id.toSeq
        if (orphans.nonEmpty) {
          service.getByIds(orphans: _*).remove()
          Map("orphan" -> orphans.size)
        } else Map.empty[String, Int]
      }
    }.getOrElse(Map("globalFailure" -> 1))
}
