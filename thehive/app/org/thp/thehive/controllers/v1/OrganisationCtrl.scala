package org.thp.thehive.controllers.v1

import play.api.http.HttpErrorHandler
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.thehive.dto.v1.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models.{Organisation, Permissions}
import org.thp.thehive.services._

@Singleton
class OrganisationCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    organisationSrv: OrganisationSrv,
    errorHandler: HttpErrorHandler,
    userSrv: UserSrv
) extends QueryableCtrl {

  import OrganisationConversion._

  override val entityName: String                           = "organisation"
  override val publicProperties: List[PublicProperty[_, _]] = organisationProperties
  override val initialQuery: Query =
    Query.init[OrganisationSteps]("listOrganisation", (graph, authContext) => organisationSrv.initSteps(graph).visible(authContext))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, OrganisationSteps, PagedResult[Organisation with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, organisationSteps, _) => organisationSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.deprecatedOutput[Organisation with Entity, OutputOrganisation]
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, OrganisationSteps](
    "getOrganisation",
    FieldsParser[IdOrName],
    (param, graph, authContext) => organisationSrv.get(param.idOrName)(graph).visible(authContext)
  )
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[OrganisationSteps, OrganisationSteps]("visible", (organisationSteps, _) => organisationSteps.visibleOrganisations),
    Query[OrganisationSteps, UserSteps]("users", (organisationSteps, _) => organisationSteps.users),
    Query[OrganisationSteps, CaseTemplateSteps]("caseTemplates", (organisationSteps, _) => organisationSteps.caseTemplates)
  )

  def create: Action[AnyContent] =
    entryPoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputOrganisation = request.body("organisation")
        for {
          _            <- userSrv.current.organisations(Permissions.manageOrganisation).get(OrganisationSrv.default.name).existsOrFail()
          user         <- userSrv.current.getOrFail()
          organisation <- organisationSrv.create(fromInputOrganisation(inputOrganisation), user)
        } yield Results.Created(organisation.toJson)
      }

  def get(organisationId: String): Action[AnyContent] =
    entryPoint("get organisation")
      .authRoTransaction(db) { implicit request => implicit graph =>
        userSrv
          .current
          .organisations
          .visibleOrganisations
          .get(organisationId)
          .getOrFail()
          .map(organisation => Results.Ok(organisation.toJson))
      }

  //  def list: Action[AnyContent] =
  //    entryPoint("list organisation")
  //      .authRoTransaction(db) { _ ⇒ implicit graph ⇒
  //          val organisations = organisationSrv.initSteps.toList
  //            .map(toOutputOrganisation)
  //          Results.Ok(Json.toJson(organisations))
  //        }

  def update(organisationId: String): Action[AnyContent] =
    entryPoint("update organisation")
      .extract("organisation", FieldsParser.update("organisation", organisationProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("organisation")
        organisationSrv
          .update(
            userSrv
              .current
              .organisations(Permissions.manageOrganisation)
              .get(organisationId),
            propertyUpdaters
          )
          .map(_ => Results.NoContent)
      }
}
