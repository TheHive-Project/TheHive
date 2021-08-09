package org.thp.thehive.services

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services.{IntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import play.api.cache.SyncCacheApi
import play.api.libs.json.JsObject

import scala.util.{Success, Try}

class CustomFieldSrv(
    auditSrv: AuditSrv,
    organisationSrv: OrganisationSrv,
    integrityCheckActor: => ActorRef @@ IntegrityCheckTag,
    cacheApi: SyncCacheApi,
    customFieldValueSrv: CustomFieldValueSrv
) extends VertexSrv[CustomField]
    with TheHiveOpsNoDeps {

  override def createEntity(e: CustomField)(implicit graph: Graph, authContext: AuthContext): Try[CustomField with Entity] = {
    integrityCheckActor ! EntityAdded("CustomField")
    cacheApi.remove("describe.v0")
    cacheApi.remove("describe.v1")
    super.createEntity(e)
  }

  def create(e: CustomField)(implicit graph: Graph, authContext: AuthContext): Try[CustomField with Entity] =
    for {
      created <- createEntity(e)
      _       <- auditSrv.customField.create(created, created.toJson)
    } yield created

  override def exists(e: CustomField)(implicit graph: Graph): Boolean = startTraversal.getByName(e.name).exists

  def delete(c: CustomField with Entity, force: Boolean)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(c).remove() // TODO use force
    cacheApi.remove("describe.v0")
    cacheApi.remove("describe.v1")
    organisationSrv.getOrFail(authContext.organisation).flatMap { organisation =>
      auditSrv.customField.delete(c, organisation)
    }
  }

  def useCount(c: CustomField with Entity)(implicit graph: Graph): Map[String, Long] =
    customFieldValueSrv
      .startTraversal
      .has(_.name, c.name)
      .in()
      .groupCount(_.byLabel)
      .headOption
      .fold(Map.empty[String, Long])(_.filterNot(_._1 == "Audit"))

  override def update(
      traversal: Traversal.V[CustomField],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[CustomField], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (customFieldSteps, updatedFields) =>
        customFieldSteps
          .clone()
          .getOrFail("CustomFields")
          .flatMap { cf =>
            cacheApi.remove("describe.v0")
            cacheApi.remove("describe.v1")
            auditSrv.customField.update(cf, updatedFields)
          }
    }

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[CustomField] =
    startTraversal.getByName(name)
}

trait CustomFieldOps { _: TraversalOps =>
  implicit class CustomFieldOpsDefs(traversal: Traversal.V[CustomField]) {
    def get(idOrName: EntityIdOrName): Traversal.V[CustomField] =
      idOrName.fold(traversal.getByIds(_), getByName)

    def getByName(name: String): Traversal.V[CustomField] = traversal.has(_.name, name)
  }
}

class CustomFieldIntegrityCheckOps(val db: Database, val service: CustomFieldSrv) extends IntegrityCheckOps[CustomField] {
  override def resolve(entities: Seq[CustomField with Entity])(implicit graph: Graph): Try[Unit] =
    entities match {
      case head :: tail =>
        tail.foreach(copyEdge(_, head))
        service.getByIds(tail.map(_._id): _*).remove()
        Success(())
      case _ => Success(())
    }

  override def globalCheck(): Map[String, Int] = Map.empty
}
