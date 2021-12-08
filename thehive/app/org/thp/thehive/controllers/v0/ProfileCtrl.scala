package org.thp.thehive.controllers.v0

import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, IndexType, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{AuthorizationError, EntityIdOrName}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputProfile
import org.thp.thehive.models.{Permissions, Profile}
import org.thp.thehive.services.{ProfileSrv, SearchSrv, TheHiveOpsNoDeps}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Failure

class ProfileCtrl(
    override val entrypoint: Entrypoint,
    profileSrv: ProfileSrv,
    override val publicData: PublicProfile,
    implicit val db: Database,
    override val queryExecutor: QueryExecutor
) extends QueryCtrl
    with TheHiveOpsNoDeps {
  def create: Action[AnyContent] =
    entrypoint("create profile")
      .extract("profile", FieldsParser[InputProfile])
      .authTransaction(db) { implicit request => implicit graph =>
        val profile: InputProfile = request.body("profile")
        if (request.isPermitted(Permissions.manageProfile))
          profileSrv.create(profile.toProfile).map(createdProfile => Results.Created(createdProfile.toJson))
        else
          Failure(AuthorizationError("You don't have permission to create profiles"))
      }

  def get(profileId: String): Action[AnyContent] =
    entrypoint("get profile")
      .authRoTransaction(db) { _ => implicit graph =>
        profileSrv
          .getOrFail(EntityIdOrName(profileId))
          .map { profile =>
            Results.Ok(profile.toJson)
          }
      }

  def update(profileId: String): Action[AnyContent] =
    entrypoint("update profile")
      .extract("profile", FieldsParser.update("profile", publicData.publicProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("profile")
        if (request.isPermitted(Permissions.manageProfile))
          profileSrv
            .update(_.get(EntityIdOrName(profileId)), propertyUpdaters)
            .flatMap { case (profileSteps, _) => profileSteps.getOrFail("Profile") }
            .map(profile => Results.Ok(profile.toJson))
        else
          Failure(AuthorizationError("You don't have permission to update profiles"))
      }

  def delete(profileId: String): Action[AnyContent] =
    entrypoint("delete profile")
      .authPermittedTransaction(db, Permissions.manageProfile) { implicit request => implicit graph =>
        profileSrv
          .getOrFail(EntityIdOrName(profileId))
          .flatMap(profileSrv.remove)
          .map(_ => Results.NoContent)
      }
}

class PublicProfile(profileSrv: ProfileSrv, searchSrv: SearchSrv) extends PublicData with TheHiveOpsNoDeps {
  val entityName: String = "profile"

  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Profile]](
    "getProfile",
    (idOrName, graph, _) => profileSrv.get(idOrName)(graph)
  )
  val initialQuery: Query =
    Query.init[Traversal.V[Profile]]("listProfile", (graph, _) => profileSrv.startTraversal(graph))

  override def pageQuery(limitedCountThreshold: Long): ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Profile], IteratorOutput](
      "page",
      (range, profileSteps, _) => profileSteps.page(range.from, range.to, withTotal = true, limitedCountThreshold)
    )
  override val outputQuery: Query = Query.output[Profile with Entity]
  val publicProperties: PublicProperties = PublicPropertyListBuilder[Profile]
    .property("keyword", UMapping.string)(
      _.select(_.empty.asInstanceOf[Traversal[String, _, _]])
        .filter[String](IndexType.fulltext) {
          case (_, t, _, Right(p))   => searchSrv("Profile", p.getValue)(t)
          case (_, t, _, Left(true)) => t
          case (_, t, _, _)          => t.empty
        }
        .readonly
    )
    .property("name", UMapping.string)(_.field.updatable)
    .property("permissions", UMapping.string.set)(_.field.updatable)
    .build
}
