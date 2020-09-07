package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperties, Query}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputOrganisation
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class OrganisationCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    @Named("with-thehive-schema") implicit val db: Database
) extends QueryableCtrl {

  override val entityName: String                 = "organisation"
  override val publicProperties: PublicProperties = properties.organisation
  override val initialQuery: Query =
    Query.init[Traversal.V[Organisation]]("listOrganisation", (graph, authContext) => organisationSrv.visibleOrganisation(graph, authContext))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Organisation], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, organisationSteps, _) => organisationSteps.richPage(range.from, range.to, range.extraData.contains("total"))(_.richOrganisation)
  )
  override val outputQuery: Query = Query.output[RichOrganisation, Traversal.V[Organisation]](_.richOrganisation)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, Traversal.V[Organisation]](
    "getOrganisation",
    FieldsParser[IdOrName],
    (param, graph, authContext) => organisationSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Organisation], Traversal.V[Organisation]]("visible", (organisationSteps, _) => organisationSteps.visibleOrganisationsFrom),
    Query[Traversal.V[Organisation], Traversal.V[User]]("users", (organisationSteps, _) => organisationSteps.users),
    Query[Traversal.V[Organisation], Traversal.V[CaseTemplate]]("caseTemplates", (organisationSteps, _) => organisationSteps.caseTemplates),
    Query[Traversal.V[Organisation], Traversal.V[Alert]]("alerts", (organisationSteps, _) => organisationSteps.alerts)
  )

  def create: Action[AnyContent] =
    entrypoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val inputOrganisation: InputOrganisation = request.body("organisation")
        for {
          user         <- userSrv.current.getOrFail("User")
          organisation <- organisationSrv.create(inputOrganisation.toOrganisation, user)
        } yield Results.Created(organisation.toJson)
      }

  def get(organisationId: String): Action[AnyContent] =
    entrypoint("get organisation")
      .authRoTransaction(db) { implicit request => implicit graph =>
        (if (request.organisation == "admin")
           organisationSrv.get(organisationId)
         else
           userSrv
             .current
             .organisations
             .visibleOrganisationsFrom
             .get(organisationId))
          .richOrganisation
          .getOrFail("Organisation")
          .map(organisation => Results.Ok(organisation.toJson))
      }

  def update(organisationId: String): Action[AnyContent] =
    entrypoint("update organisation")
      .extract("organisation", FieldsParser.update("organisation", properties.organisation))
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("organisation")
        for {
          organisation <- organisationSrv.getOrFail(organisationId)
          _            <- organisationSrv.update(organisationSrv.get(organisation), propertyUpdaters)
        } yield Results.NoContent
      }
}
