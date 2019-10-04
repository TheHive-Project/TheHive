package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.thehive.dto.v0.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models.{Organisation, Permissions}
import org.thp.thehive.services._
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class OrganisationCtrl @Inject()(entryPoint: EntryPoint, db: Database, organisationSrv: OrganisationSrv, userSrv: UserSrv, auditSrv: AuditSrv)
    extends QueryableCtrl {
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
  override val outputQuery: Query = Query.output[Organisation with Entity, OutputOrganisation]
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
        val inputOrganisation: InputOrganisation = request.body("organisation")
        for {
          _   <- userSrv.current.organisations(Permissions.manageOrganisation).get(OrganisationSrv.default.name).existsOrFail()
          org <- organisationSrv.create(fromInputOrganisation(inputOrganisation))
          _   <- auditSrv.organisation.create(org)
        } yield Results.Created(org.toJson)
      }

  def get(organisationId: String): Action[AnyContent] =
    entryPoint("get an organisation")
      .authRoTransaction(db) { implicit request => implicit graph =>
        organisationSrv
          .get(organisationId)
          .visible
          .getOrFail()
          .map(organisation => Results.Ok(organisation.toJson))
      }

  def list: Action[AnyContent] =
    entryPoint("list organisation")
      .authRoTransaction(db) { _ => implicit graph =>
        val organisations = organisationSrv
          .initSteps
          .toIterator
          .map(_.toJson)
          .toSeq
        Success(Results.Ok(JsArray(organisations)))
      }

  def update(organisationId: String): Action[AnyContent] =
    entryPoint("update organisation")
      .extract("organisation", FieldsParser.update("organisation", organisationProperties))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("organisation")

        for {
          _ <- organisationSrv
            .update(
              userSrv
                .current
                .organisations(Permissions.manageOrganisation)
                .get(organisationId),
              propertyUpdaters
            )
        } yield Results.NoContent
      }

  def link(fromOrganisationId: String, toOrganisationId: String): Action[AnyContent] =
    entryPoint("link organisations")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          fromOrg <- userSrv
            .current
            .organisations(Permissions.manageOrganisation)
            .get(fromOrganisationId)
            .getOrFail()
          toOrg <- organisationSrv.getOrFail(toOrganisationId)
          _     <- organisationSrv.link(fromOrg, toOrg)
        } yield Results.Created
      }

  def unlink(fromOrganisationId: String, toOrganisationId: String): Action[AnyContent] =
    entryPoint("unlink organisations")
      .authTransaction(db) { implicit request => implicit graph =>
        for {
          fromOrg <- userSrv
            .current
            .organisations(Permissions.manageOrganisation)
            .get(fromOrganisationId)
            .getOrFail()
          toOrg <- organisationSrv.getOrFail(toOrganisationId)
          _ = organisationSrv.unlink(fromOrg, toOrg)
        } yield Results.NoContent
      }

  def listLinks(organisationId: String): Action[AnyContent] =
    entryPoint("list organisation links")
      .authRoTransaction(db) { implicit req => implicit graph =>
        val org = userSrv
          .current
          .organisations
          .get(organisationId)
          .getOrFail()

        org.map(
          o =>
            Results.Ok(
              JsArray(
                organisationSrv
                  .get(o)
                  .links
                  .toList
                  .map(_.toJson)
              )
            )
        )
      }
}
