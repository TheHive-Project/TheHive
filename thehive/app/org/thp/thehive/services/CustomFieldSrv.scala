package org.thp.thehive.services
import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.models.CustomField

@Singleton
class CustomFieldSrv @Inject()(implicit db: Database) extends VertexSrv[CustomField, CustomFieldSteps] {
  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): CustomFieldSteps = new CustomFieldSteps(raw)

  override def get(id: String)(implicit graph: Graph): CustomFieldSteps =
    if (db.isValidId(id)) super.get(id)
    else initSteps.getByName(id)
}

class CustomFieldSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[CustomField, CustomFieldSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): CustomFieldSteps = new CustomFieldSteps(raw)

  override def get(id: String): CustomFieldSteps =
    if (db.isValidId(id)) getById(id)
    else getByName(id)

  def getById(id: String): CustomFieldSteps = new CustomFieldSteps(raw.hasId(id))

  def getByName(name: String): CustomFieldSteps = new CustomFieldSteps(raw.has(Key("name") of name))

}
