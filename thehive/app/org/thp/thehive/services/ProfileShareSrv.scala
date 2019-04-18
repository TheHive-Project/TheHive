package org.thp.thehive.services

import java.util.UUID

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

import scala.util.Try

@Singleton
class ProfileSrv @Inject()(implicit val db: Database) extends VertexSrv[Profile, ProfileSteps] {
  override val initialValues: Seq[Profile] = Seq(
    Profile("admin", Permissions.all),
    Profile("analyst", Set(Permissions.manageCase, Permissions.manageAlert)),
    Profile("read-only", Set.empty)
  )

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ProfileSteps = new ProfileSteps(raw)

  lazy val admin: Profile with Entity = db.tryTransaction(graph ⇒ getOrFail("admin")(graph)).get
}

@EntitySteps[Profile]
class ProfileSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Profile, ProfileSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ProfileSteps = new ProfileSteps(raw)

  override def get(id: String): ProfileSteps =
    Try(UUID.fromString(id))
      .map(_ ⇒ getById(id))
      .getOrElse(getByName(id))

  def getById(id: String): ProfileSteps = new ProfileSteps(raw.has(Key("_id") of id))

  def getByName(name: String): ProfileSteps = new ProfileSteps(raw.has(Key("name") of name))
}
