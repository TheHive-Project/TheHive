package org.thp.thehive.services
import scala.util.Try

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.models.CustomField

@Singleton
class CustomFieldSrv @Inject()(implicit db: Database, auditSrv: AuditSrv) extends VertexSrv[CustomField, CustomFieldSteps] {
  def create(e: CustomField)(implicit graph: Graph, authContext: AuthContext): Try[CustomField with Entity] = createEntity(e)

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CustomFieldSteps = new CustomFieldSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): CustomFieldSteps =
    if (db.isValidId(idOrName)) super.getByIds(idOrName)
    else initSteps.getByName(idOrName)
}

class CustomFieldSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[CustomField, CustomFieldSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): CustomFieldSteps = new CustomFieldSteps(raw)

  def get(idOrName: String): CustomFieldSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): CustomFieldSteps = new CustomFieldSteps(raw.has(Key("name") of name))

}
