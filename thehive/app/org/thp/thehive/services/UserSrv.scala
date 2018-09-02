package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.models._
import org.thp.scalligraph.services.VertexSrv
import org.thp.scalligraph.{EntitySteps, PrivateField}
import org.thp.thehive.models.User

@Singleton
class UserSrv @Inject()(implicit val db: Database) extends VertexSrv[User] {
  override def steps(implicit graph: Graph): UserSteps           = new UserSteps(graph.V.hasLabel(model.label))
  override def get(id: String)(implicit graph: Graph): UserSteps = steps.get(id)
}

@EntitySteps[User]
class UserSteps(raw: GremlinScala[Vertex])(implicit db: Database) extends BaseVertexSteps[User, UserSteps](raw) {
  @PrivateField override def newInstance(raw: GremlinScala[Vertex]): UserSteps = new UserSteps(raw)

  def get(id: String): UserSteps = new UserSteps(raw.coalesce(_.has(Key("login") of id), _.hasId(id)))
}
