package org.thp.thehive.services

import scala.util.{Failure, Try}

import play.api.libs.json.JsObject

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.{BadRequestError, EntitySteps}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._

object ProfileSrv {
  lazy val admin: Profile = Profile("admin", Permissions.adminPermissions)

  lazy val analyst: Profile = Profile(
    "analyst",
    Set(
      Permissions.manageCase,
      Permissions.manageObservable,
      Permissions.manageAlert,
      Permissions.manageTask,
      Permissions.manageAction,
      Permissions.manageShare,
      Permissions.manageAnalyse,
      Permissions.managePage
    )
  )
  lazy val readonly: Profile = Profile("read-only", Set.empty)
  lazy val orgAdmin: Profile = Profile("org-admin", Permissions.forScope("organisation"))

  def isEditable(profile: Profile): Boolean = profile.name != admin.name && profile.name != orgAdmin.name
}

@Singleton
class ProfileSrv @Inject() (auditSrv: AuditSrv)(implicit val db: Database) extends VertexSrv[Profile, ProfileSteps] {

  lazy val orgAdmin: Profile with Entity = db.roTransaction(graph => getOrFail(ProfileSrv.orgAdmin.name)(graph)).get
  override val initialValues: Seq[Profile] = Seq(
    ProfileSrv.admin,
    ProfileSrv.orgAdmin,
    ProfileSrv.analyst,
    ProfileSrv.readonly
  )

  def create(profile: Profile)(implicit graph: Graph, authContext: AuthContext): Try[Profile with Entity] =
    for {
      createdProfile <- createEntity(profile)
      _              <- auditSrv.profile.create(createdProfile, createdProfile.toJson)
    } yield createdProfile

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ProfileSteps = new ProfileSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ProfileSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  def remove(profile: Profile with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    if (!ProfileSrv.isEditable(profile))
      Failure(BadRequestError(s"Profile ${profile.name} cannot be removed"))
    else if (get(profile).filter(_.or(_.roles, _.shares)).exists())
      Failure(BadRequestError(s"Profile ${profile.name} is used"))
    else {
      get(profile).remove()
      auditSrv.profile.delete(profile)
    }

  override def update(
      steps: ProfileSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(ProfileSteps, JsObject)] =
    if (steps.newInstance().toIterator.exists(!ProfileSrv.isEditable(_)))
      Failure(BadRequestError(s"Profile is not editable"))
    else super.update(steps, propertyUpdaters)
}

@EntitySteps[Profile]
class ProfileSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends VertexSteps[Profile](raw) {
  override def newInstance(newRaw: GremlinScala[Vertex]): ProfileSteps = new ProfileSteps(newRaw)
  override def newInstance(): ProfileSteps                             = new ProfileSteps(raw.clone())

  def roles = new RoleSteps(raw.inTo[RoleProfile])

  def shares = new ShareSteps(raw.inTo[ShareProfile])

  def get(idOrName: String): ProfileSteps =
    if (db.isValidId(idOrName)) this.getByIds(idOrName)
    else getByName(idOrName)

  def getByName(name: String): ProfileSteps = new ProfileSteps(raw.has(Key("name") of name))

  def contains(permission: Permission): ProfileSteps =
    this.has("permissions", permission)
}
