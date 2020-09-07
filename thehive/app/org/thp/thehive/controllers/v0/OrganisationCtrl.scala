package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.NotFoundError
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputOrganisation
import org.thp.thehive.models.{CaseTemplate, Organisation, Permissions, User}
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.{Failure, Success}

@Singleton
class OrganisationCtrl @Inject() (
    override val entrypoint: Entrypoint,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    @Named("with-thehive-schema") implicit override val db: Database,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicOrganisation
) extends QueryCtrl {
  def create: Action[AnyContent] =
    entrypoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputOrganisation: InputOrganisation = request.body("organisation")
        for {
          _   <- userSrv.current.organisations(Permissions.manageOrganisation).get(Organisation.administration.name).existsOrFail
          org <- organisationSrv.create(inputOrganisation.toOrganisation)

        } yield Results.Created(org.toJson)
      }

  def get(organisationId: String): Action[AnyContent] =
    entrypoint("get an organisation")
      .authRoTransaction(db) { implicit request => implicit graph =>
        organisationSrv
          .get(organisationId)
          .visible
          .richOrganisation
          .getOrFail("Organisation")
          .map(organisation => Results.Ok(organisation.toJson))
      }

  def list: Action[AnyContent] =
    entrypoint("list organisation")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val organisations = organisationSrv
          .startTraversal
          .visible
          .richOrganisation
          .toSeq

        Success(Results.Ok(organisations.toJson))
      }

  def update(organisationId: String): Action[AnyContent] =
    entrypoint("update organisation")
      .extract("organisation", FieldsParser.update("organisation", publicData.publicProperties))
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("organisation")

        for {
          organisation <- organisationSrv.getOrFail(organisationId)
          _            <- organisationSrv.update(organisationSrv.get(organisation), propertyUpdaters)
        } yield Results.NoContent
      }

  def link(fromOrganisationId: String, toOrganisationId: String): Action[AnyContent] =
    entrypoint("link organisations")
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        for {
          fromOrg <- organisationSrv.getOrFail(fromOrganisationId)
          toOrg   <- organisationSrv.getOrFail(toOrganisationId)
          _       <- organisationSrv.doubleLink(fromOrg, toOrg)
        } yield Results.Created
      }

  def bulkLink(fromOrganisationId: String): Action[AnyContent] =
    entrypoint("link multiple organisations")
      .extract("organisations", FieldsParser.string.sequence.on("organisations"))
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val organisations: Seq[String] = request.body("organisations")

        for {
          fromOrg <- organisationSrv.getOrFail(fromOrganisationId)
          _       <- organisationSrv.updateLink(fromOrg, organisations)
        } yield Results.Created
      }

  def unlink(fromOrganisationId: String, toOrganisationId: String): Action[AnyContent] =
    entrypoint("unlink organisations")
      .authPermittedTransaction(db, Permissions.manageOrganisation) { _ => implicit graph =>
        for {
          fromOrg <- organisationSrv.getOrFail(fromOrganisationId)
          toOrg   <- organisationSrv.getOrFail(toOrganisationId)
          _ <-
            if (organisationSrv.linkExists(fromOrg, toOrg)) Success(organisationSrv.doubleUnlink(fromOrg, toOrg))
            else Failure(NotFoundError(s"Organisation $fromOrganisationId is not linked to $toOrganisationId"))
        } yield Results.NoContent
      }

  def listLinks(organisationId: String): Action[AnyContent] =
    entrypoint("list organisation links")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val isInDefaultOrganisation = userSrv.current.organisations.get(Organisation.administration.name).exists
        val organisation =
          if (isInDefaultOrganisation)
            organisationSrv.get(organisationId)
          else
            userSrv
              .current
              .organisations
              .get(organisationId)
        val organisations = organisation.links.toSeq

        Success(Results.Ok(organisations.toJson))
      }
}

@Singleton
class PublicOrganisation @Inject() (organisationSrv: OrganisationSrv) extends PublicData {
  override val entityName: String = "organisation"

  override val initialQuery: Query =
    Query.init[Traversal.V[Organisation]]("listOrganisation", (graph, authContext) => organisationSrv.startTraversal(graph).visible(authContext))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Organisation], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, organisationSteps, _) => organisationSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[Organisation with Entity]
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[Organisation]](
    "getOrganisation",
    FieldsParser[IdOrName],
    (param, graph, authContext) => organisationSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Organisation], Traversal.V[Organisation]]("visible", (organisationSteps, _) => organisationSteps.visibleOrganisationsFrom),
    Query[Traversal.V[Organisation], Traversal.V[User]]("users", (organisationSteps, _) => organisationSteps.users),
    Query[Traversal.V[Organisation], Traversal.V[CaseTemplate]]("caseTemplates", (organisationSteps, _) => organisationSteps.caseTemplates)
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[Organisation]
    .property("name", UMapping.string)(_.field.updatable)
    .property("description", UMapping.string)(_.field.updatable)
    .build
}
