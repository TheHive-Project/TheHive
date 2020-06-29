package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputOrganisation
import org.thp.thehive.models.{Permissions, RichOrganisation}
import org.thp.thehive.services._
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class OrganisationCtrl @Inject() (
    entrypoint: Entrypoint,
    @Named("with-thehive-schema") db: Database,
    properties: Properties,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
) extends QueryableCtrl {

  override val entityName: String                           = "organisation"
  override val publicProperties: List[PublicProperty[_, _]] = properties.organisation ::: metaProperties[OrganisationSteps]
  override val initialQuery: Query =
    Query.init[OrganisationSteps]("listOrganisation", (graph, authContext) => organisationSrv.initSteps(graph).visible(authContext))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, OrganisationSteps, PagedResult[RichOrganisation]](
    "page",
    FieldsParser[OutputParam],
    (range, organisationSteps, _) => organisationSteps.richPage(range.from, range.to, withTotal = true)(_.richOrganisation)
  )
  override val outputQuery: Query = Query.output[RichOrganisation, OrganisationSteps](_.richOrganisation)
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, OrganisationSteps](
    "getOrganisation",
    FieldsParser[IdOrName],
    (param, graph, authContext) => organisationSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[OrganisationSteps, OrganisationSteps]("visible", (organisationSteps, _) => organisationSteps.visibleOrganisationsFrom),
    Query[OrganisationSteps, UserSteps]("users", (organisationSteps, _) => organisationSteps.users),
    Query[OrganisationSteps, CaseTemplateSteps]("caseTemplates", (organisationSteps, _) => organisationSteps.caseTemplates),
    Query[OrganisationSteps, AlertSteps]("alerts", (organisationSteps, _) => organisationSteps.alerts)
  )

  def create: Action[AnyContent] =
    entrypoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val inputOrganisation: InputOrganisation = request.body("organisation")
        for {
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
