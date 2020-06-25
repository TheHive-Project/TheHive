package org.thp.thehive.services

import akka.actor.ActorRef
import gremlin.scala._
import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.scalligraph.{BadRequestError, EntitySteps}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import play.api.libs.json.JsObject

import scala.util.{Failure, Success, Try}

@Singleton
class ProfileSrv @Inject() (auditSrv: AuditSrv, @Named("integrity-check-actor") integrityCheckActor: ActorRef)(
    implicit @Named("with-thehive-schema") val db: Database
) extends VertexSrv[Profile, ProfileSteps] {

  lazy val orgAdmin: Profile with Entity = db.roTransaction(graph => getOrFail(Profile.orgAdmin.name)(graph)).get

  override def createEntity(e: Profile)(implicit graph: Graph, authContext: AuthContext): Try[Profile with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("Profile")
    super.createEntity(e)
  }

  def create(profile: Profile)(implicit graph: Graph, authContext: AuthContext): Try[Profile with Entity] =
    for {
      createdProfile <- createEntity(profile)
      _              <- auditSrv.profile.create(createdProfile, createdProfile.toJson)
    } yield createdProfile

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ProfileSteps = new ProfileSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): ProfileSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByName(idOrName)

  override def exists(e: Profile)(implicit graph: Graph): Boolean = initSteps.getByName(e.name).exists()

  def remove(profile: Profile with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    if (!profile.isEditable)
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
    if (steps.newInstance().toIterator.exists(!_.isEditable))
      Failure(BadRequestError(s"Profile is not editable"))
    else super.update(steps, propertyUpdaters)
}

@EntitySteps[Profile]
class ProfileSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-schema") db: Database, graph: Graph) extends VertexSteps[Profile](raw) {
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

class ProfileIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: ProfileSrv)
    extends IntegrityCheckOps[Profile] {
  override def resolve(entities: List[Profile with Entity])(implicit graph: Graph): Try[Unit] = entities match {
    case head :: tail =>
      tail.foreach(copyEdge(_, head))
      tail.foreach(service.get(_).remove())
      Success(())
    case _ => Success(())
  }
}
