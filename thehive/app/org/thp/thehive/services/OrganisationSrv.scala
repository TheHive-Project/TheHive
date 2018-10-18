package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

@Singleton
class OrganisationSrv @Inject()()(implicit db: Database) extends VertexSrv[Organisation, OrganisationSteps] {

  override val initialValues: Seq[Organisation]                          = Seq(Organisation("default"))
  override def get(id: String)(implicit graph: Graph): OrganisationSteps = initSteps.get(id)
  override def steps(raw: GremlinScala[Vertex]): OrganisationSteps       = new OrganisationSteps(raw)
}

@EntitySteps[Case]
class OrganisationSteps(raw: GremlinScala[Vertex])(implicit db: Database) extends BaseVertexSteps[Organisation, OrganisationSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): OrganisationSteps = new OrganisationSteps(raw)
//  override def filter(f: EntityFilter[Vertex]): OrganisationSteps        = newInstance(f(raw))

  def getById(id: String): OrganisationSteps = newInstance(raw.has(Key("_id") of id))

  def getByName(name: String): OrganisationSteps = newInstance(raw.has(Key("name") of name))

  def get(id: String): OrganisationSteps = new OrganisationSteps(raw.coalesce(_.has(Key("_id") of id), _.has(Key("name") of id)))
}
