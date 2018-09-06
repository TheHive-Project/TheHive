package org.thp.thehive.services
import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models.{BaseVertexSteps, Database}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.models.CustomField

@Singleton
class CustomFieldSrv @Inject()(implicit db: Database) extends VertexSrv[CustomField, CustomFieldSteps] {
  override def steps(raw: GremlinScala[Vertex]): CustomFieldSteps       = new CustomFieldSteps(raw)
  override def get(id: String)(implicit graph: Graph): CustomFieldSteps = initSteps.get(id)
}

class CustomFieldSteps(raw: GremlinScala[Vertex])(implicit db: Database) extends BaseVertexSteps[CustomField, CustomFieldSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): CustomFieldSteps = new CustomFieldSteps(raw)
  def get(id: String): CustomFieldSteps                                 = new CustomFieldSteps(raw.coalesce(_.hasId(id), _.has(Key("name") of id)))
}
