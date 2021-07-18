package org.thp.thehive.controllers.v0

import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{EntityIdOrName, EntityName, RichSeq}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputOrganisation
import org.thp.thehive.models.{CaseTemplate, Organisation, Permissions, User}
import org.thp.thehive.services._
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

class OrganisationCtrl(
    override val entrypoint: Entrypoint,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    implicit override val db: Database,
    override val queryExecutor: QueryExecutor,
    override val publicData: PublicOrganisation
) extends QueryCtrl
    with TheHiveOpsNoDeps {

  def create: Action[AnyContent] =
    entrypoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputOrganisation: InputOrganisation = request.body("organisation")
        for {
          _   <- userSrv.current.organisations(Permissions.manageOrganisation).get(EntityName(Organisation.administration.name)).existsOrFail
          org <- organisationSrv.create(inputOrganisation.toOrganisation)

        } yield Results.Created(org.toJson)
      }

  def get(organisationId: String): Action[AnyContent] =
    entrypoint("get an organisation")
      .authRoTransaction(db) { implicit request => implicit graph =>
        organisationSrv
          .get(EntityIdOrName(organisationId))
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
          organisation <- organisationSrv.getOrFail(EntityIdOrName(organisationId))
          _            <- organisationSrv.update(organisationSrv.get(organisation), propertyUpdaters)
        } yield Results.NoContent
      }

  def link(orgAId: String, orgBId: String): Action[AnyContent] =
    entrypoint("link organisations")
      .extract("linkType", FieldsParser.string.optional.on("linkType"))
      .extract("otherLinkType", FieldsParser.string.optional.on("otherLinkType"))
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val linkTypeAB: Option[String] = request.body("linkType")
        val linkTypeBA: Option[String] = request.body("otherLinkType")
        for {
          orgA <- organisationSrv.getOrFail(EntityIdOrName(orgAId))
          orgB <- organisationSrv.getOrFail(EntityIdOrName(orgBId))
          _    <- organisationSrv.link(orgA, orgB, linkTypeAB.getOrElse("default"), linkTypeBA.getOrElse("default"))
        } yield Results.Created
      }

  def bulkLink(fromOrganisationId: String): Action[AnyContent] =
    entrypoint("link multiple organisations")
      .extract("organisations", FieldsParser.string.sequence.on("organisations"))
      .extract("linkType", FieldsParser.string.optional.on("linkType"))
      .extract("otherLinkType", FieldsParser.string.optional.on("otherLinkType"))
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val organisations: Seq[String]    = request.body("organisations")
        val linkType: Option[String]      = request.body("linkType")
        val otherLinkType: Option[String] = request.body("otherLinkType")

        for {
          fromOrg <- organisationSrv.getOrFail(EntityIdOrName(fromOrganisationId))
          toOrgs  <- organisations.toTry(o => organisationSrv.getOrFail(EntityIdOrName(o)))
          _       <- toOrgs.toTry(o => organisationSrv.link(fromOrg, o, linkType.getOrElse("default"), otherLinkType.getOrElse("default")))
        } yield Results.Created
      }

  def unlink(orgAId: String, orgBId: String): Action[AnyContent] =
    entrypoint("unlink organisations")
      .authPermittedTransaction(db, Permissions.manageOrganisation) { _ => implicit graph =>
        for {
          orgA <- organisationSrv.getOrFail(EntityIdOrName(orgAId))
          orgB <- organisationSrv.getOrFail(EntityIdOrName(orgBId))
          _    <- organisationSrv.unlink(orgA, orgB)
        } yield Results.NoContent
      }

  def listLinks(organisationId: String): Action[AnyContent] =
    entrypoint("list organisation links")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val isInDefaultOrganisation = userSrv.current.organisations.get(EntityName(Organisation.administration.name)).exists
        val organisation =
          if (isInDefaultOrganisation)
            organisationSrv.get(EntityIdOrName(organisationId))
          else
            userSrv
              .current
              .organisations
              .get(EntityIdOrName(organisationId))
        val organisations = organisation.links.toSeq

        Success(Results.Ok(organisations.toJson))
      }
}

class PublicOrganisation(organisationSrv: OrganisationSrv) extends PublicData with TheHiveOpsNoDeps {
  override val entityName: String = "organisation"

  override val initialQuery: Query =
    Query.init[Traversal.V[Organisation]]("listOrganisation", (graph, authContext) => organisationSrv.startTraversal(graph).visible(authContext))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Organisation], IteratorOutput](
    "page",
    (range, organisationSteps, _) => organisationSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[Organisation with Entity]
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Organisation]](
    "getOrganisation",
    (idOrName, graph, authContext) => organisationSrv.get(idOrName)(graph).visible(authContext)
  )
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Organisation], Traversal.V[Organisation]]("visible", (organisationSteps, _) => organisationSteps.visibleOrganisations),
    Query[Traversal.V[Organisation], Traversal.V[User]]("users", (organisationSteps, _) => organisationSteps.users.dedup),
    Query[Traversal.V[Organisation], Traversal.V[CaseTemplate]]("caseTemplates", (organisationSteps, _) => organisationSteps.caseTemplates)
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[Organisation]
    .property("name", UMapping.string)(_.field.updatable)
    .property("description", UMapping.string)(_.field.updatable)
    .property("taskRule", UMapping.string)(_.field.updatable)
    .property("observableRule", UMapping.string)(_.field.updatable)
    .build
}
