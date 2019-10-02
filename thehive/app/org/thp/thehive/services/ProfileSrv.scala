package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.thehive.models._

import scala.util.Try

object ProfileSrv {
  val admin = Profile("admin", Permissions.adminPermissions)

  val analyst = Profile(
    "analyst",
    Set(
      Permissions.manageCase,
      Permissions.manageObservable,
      Permissions.manageAlert,
      Permissions.manageTask,
      Permissions.manageAction
    )
  )
  val readonly = Profile("read-only", Set.empty)
  val orgAdmin = Profile("org-admin", Permissions.all -- Permissions.restrictedPermissions)
  val all      = Profile("all", Permissions.all)
}

@Singleton
class ProfileSrv @Inject()(auditSrv: AuditSrv)(implicit val db: Database) extends VertexSrv[Profile, ProfileSteps] {

  lazy val all: Profile with Entity = db.roTransaction(graph => getOrFail(ProfileSrv.all.name)(graph)).get
  override val initialValues: Seq[Profile] = Seq(
    ProfileSrv.admin,
    ProfileSrv.orgAdmin,
    ProfileSrv.analyst,
    ProfileSrv.readonly,
    ProfileSrv.all
  )

  def create(profile: Profile)(implicit graph: Graph, authContext: AuthContext): Try[Profile with Entity] =
    for {
      createdProfile <- createEntity(profile)
      _              <- auditSrv.profile.create(createdProfile, None)
    } yield createdProfile

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ProfileSteps = new ProfileSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ProfileSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  def remove(profile: Profile with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    for {
      _ <- Try(get(profile).remove())
      _ <- auditSrv.profile.delete(profile)
    } yield ()
}

@EntitySteps[Profile]
class ProfileSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Profile, ProfileSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ProfileSteps = new ProfileSteps(raw)

  def get(idOrName: String): ProfileSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): ProfileSteps = new ProfileSteps(raw.has(Key("name") of name))
}
