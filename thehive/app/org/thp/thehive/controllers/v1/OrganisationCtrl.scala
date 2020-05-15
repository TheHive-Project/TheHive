package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputOrganisation
import org.thp.thehive.models.{Organisation, Permissions}
import org.thp.thehive.services._
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class OrganisationCtrl @Inject() (
    entrypoint: Entrypoint,
    db: Database,
    properties: Properties,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
) extends QueryableCtrl {

  override val entityName: String                           = "organisation"
  override val publicProperties: List[PublicProperty[_, _]] = properties.organisation ::: metaProperties[OrganisationSteps]
  override val initialQuery: Query =
    Query.init[OrganisationSteps]("listOrganisation", (graph, authContext) => organisationSrv.initSteps(graph).visible(authContext))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, OrganisationSteps, PagedResult[Organisation with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, organisationSteps, _) => organisationSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[Organisation with Entity]
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, OrganisationSteps](
    "getOrganisation",
    FieldsParser[IdOrName],
    (param, graph, authContext) => organisationSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[OrganisationSteps, OrganisationSteps]("visible", (organisationSteps, _) => organisationSteps.visibleOrganisationsFrom),
    Query[OrganisationSteps, UserSteps]("users", (organisationSteps, _) => organisationSteps.users),
    Query[OrganisationSteps, CaseTemplateSteps]("caseTemplates", (organisationSteps, _) => organisationSteps.caseTemplates)
  )

  def create: Action[AnyContent] =
    entrypoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputOrganisation: InputOrganisation = request.body("organisation")
        for {
          _            <- userSrv.current.organisations(Permissions.manageOrganisation).get(OrganisationSrv.administration.name).existsOrFail()
          user         <- userSrv.current.getOrFail()
          organisation <- organisationSrv.create(inputOrganisation.toOrganisation, user)
        } yield Results.Created(organisation.toJson)
      }

  def get(organisationId: String): Action[AnyContent] =
    entrypoint("get organisation")
      .authRoTransaction(db) { implicit request => implicit graph =>
        userSrv
          .current
          .organisations
          .visibleOrganisationsFrom
          .get(organisationId)
          .getOrFail()
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
