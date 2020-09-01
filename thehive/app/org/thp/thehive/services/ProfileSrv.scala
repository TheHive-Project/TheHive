package org.thp.thehive.services

import akka.actor.ActorRef
import javax.inject.{Inject, Named, Provider, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.BadRequestError
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.models._
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.Traversal
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services.ProfileOps._
import play.api.libs.json.JsObject

import scala.util.{Failure, Success, Try}

@Singleton
class ProfileSrv @Inject() (
    auditSrv: AuditSrv,
    organisationSrvProvider: Provider[OrganisationSrv],
    @Named("integrity-check-actor") integrityCheckActor: ActorRef
)(
    implicit @Named("with-thehive-schema") val db: Database
) extends VertexSrv[Profile] {
  lazy val organisationSrv: OrganisationSrv = organisationSrvProvider.get
  lazy val orgAdmin: Profile with Entity    = db.roTransaction(graph => getOrFail(Profile.orgAdmin.name)(graph)).get

  override def createEntity(e: Profile)(implicit graph: Graph, authContext: AuthContext): Try[Profile with Entity] = {
    integrityCheckActor ! IntegrityCheckActor.EntityAdded("Profile")
    super.createEntity(e)
  }

  def create(profile: Profile)(implicit graph: Graph, authContext: AuthContext): Try[Profile with Entity] =
    for {
      createdProfile <- createEntity(profile)
      _              <- auditSrv.profile.create(createdProfile, createdProfile.toJson)
    } yield createdProfile

  override def get(idOrName: String)(implicit graph: Graph): Traversal.V[Profile] =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else startTraversal.getByName(idOrName)

  override def exists(e: Profile)(implicit graph: Graph): Boolean = startTraversal.getByName(e.name).exists

  def remove(profile: Profile with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    if (!profile.isEditable)
      Failure(BadRequestError(s"Profile ${profile.name} cannot be removed"))
    else if (get(profile).filter(_.or(_.roles, _.shares)).exists)
      Failure(BadRequestError(s"Profile ${profile.name} is used"))
    else
      organisationSrv.getOrFail(authContext.organisation).flatMap { organisation =>
        get(profile).remove()
        auditSrv.profile.delete(profile, organisation)
      }

  override def update(
      traversal: Traversal.V[Profile],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[Profile], JsObject)] =
    if (traversal.clone().toIterator.exists(!_.isEditable))
      Failure(BadRequestError(s"Profile is not editable"))
    else super.update(traversal, propertyUpdaters)
}

object ProfileOps {

  implicit class ProfileOpsDefs(traversal: Traversal.V[Profile]) {

    def roles: Traversal.V[Role] = traversal.in[RoleProfile].v[Role]

    def shares: Traversal.V[Share] = traversal.in[ShareProfile].v[Share]

    def get(idOrName: String)(implicit db: Database): Traversal.V[Profile] =
      if (db.isValidId(idOrName)) traversal.getByIds(idOrName)
      else getByName(idOrName)

    def getByName(name: String): Traversal.V[Profile] = traversal.has("name", name)

    def contains(permission: Permission): Traversal.V[Profile] =
      traversal.has("permissions", permission)
  }

}

class ProfileIntegrityCheckOps @Inject() (@Named("with-thehive-schema") val db: Database, val service: ProfileSrv)
    extends IntegrityCheckOps[Profile] {
  override def resolve(entities: Seq[Profile with Entity])(implicit graph: Graph): Try[Unit] = entities match {
    case head :: tail =>
      tail.foreach(copyEdge(_, head))
      service.getByIds(tail.map(_._id): _*).remove()
      Success(())
    case _ => Success(())
  }
}
