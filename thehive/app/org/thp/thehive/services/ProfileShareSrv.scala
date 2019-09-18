package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

object ProfileSrv {
  val admin    = Profile("admin", Permissions.all)
  val analyst  = Profile("analyst", Set(Permissions.manageCase, Permissions.manageAlert, Permissions.manageTask))
  val readonly = Profile("read-only", Set.empty)
}

@Singleton
class ProfileSrv @Inject()(implicit val db: Database) extends VertexSrv[Profile, ProfileSteps] {
  lazy val admin: Profile with Entity = db.roTransaction(graph => getOrFail(ProfileSrv.admin.name)(graph)).get
  override val initialValues: Seq[Profile] = Seq(
    ProfileSrv.admin,
    ProfileSrv.analyst,
    ProfileSrv.readonly
  )

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ProfileSteps = new ProfileSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ProfileSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)
}

@EntitySteps[Profile]
class ProfileSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Profile, ProfileSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ProfileSteps = new ProfileSteps(raw)

  def get(idOrName: String): ProfileSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): ProfileSteps = new ProfileSteps(raw.has(Key("name") of name))
}
