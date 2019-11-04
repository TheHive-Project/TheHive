package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputOrganisation
import org.thp.thehive.models.{Organisation, OrganisationOrganisation, Permissions}
import org.thp.thehive.services._

@Singleton
class OrganisationCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    properties: Properties,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv
) extends QueryableCtrl {

  override val entityName: String                           = "organisation"
  override val publicProperties: List[PublicProperty[_, _]] = properties.organisation
  override val initialQuery: Query =
    Query.init[OrganisationSteps]("listOrganisation", (graph, authContext) => organisationSrv.initSteps(graph).visible(authContext))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, OrganisationSteps, PagedResult[Organisation with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, organisationSteps, _) => organisationSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[Organisation with Entity]()
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
    entryPoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputOrganisation: InputOrganisation = request.body("organisation")
        for {
          _   <- userSrv.current.organisations(Permissions.manageOrganisation).get(OrganisationSrv.administration.name).existsOrFail()
          org <- organisationSrv.create(inputOrganisation.toOrganisation)

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
      .authRoTransaction(db) { implicit request => implicit graph =>
        val organisations = organisationSrv
          .initSteps
          .visible
          .toIterator
          .map(_.toJson)
          .toSeq
        Success(Results.Ok(JsArray(organisations)))
      }

  def update(organisationId: String): Action[AnyContent] =
    entryPoint("update organisation")
      .extract("organisation", FieldsParser.update("organisation", properties.organisation))
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("organisation")

        for {
          _ <- organisationSrv
            .update(
              organisationSrv.get(organisationId),
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
          toOrg <- userSrv
            .current
            .organisations(Permissions.manageOrganisation)
            .get(toOrganisationId)
            .getOrFail()
          _ <- organisationSrv.link(fromOrg, toOrg)
        } yield Results.Created
      }

  def bulkLink(fromOrganisationId: String): Action[AnyContent] =
    entryPoint("link multiple organisations")
      .extract("organisations", FieldsParser.string.sequence.on("organisations"))
      .authTransaction(db) { implicit request => implicit graph =>
        val organisations: Seq[String] = request.body("organisations")
        val (_, failures) = {
          for {
            fromOrg <- userSrv
              .current
              .organisations(Permissions.manageOrganisation)
              .get(fromOrganisationId)
              .headOption()
              .toSeq
            _ = organisationSrv.get(fromOrg).outToE[OrganisationOrganisation].remove()
            orgId <- organisations
            toOrg <- userSrv
              .current
              .organisations(Permissions.manageOrganisation)
              .get(orgId)
              .headOption()
          } yield organisationSrv.link(fromOrg, toOrg)
        } partition (_.isSuccess)

        if (failures.nonEmpty) Success(Results.InternalServerError(failures.map(_.failed.get).head.getMessage))
        else Success(Results.Created)
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
          toOrg <- userSrv
            .current
            .organisations(Permissions.manageOrganisation)
            .get(toOrganisationId)
            .getOrFail()
          _ = organisationSrv.unlink(fromOrg, toOrg)
        } yield Results.NoContent
      }

  def listLinks(organisationId: String): Action[AnyContent] =
    entryPoint("list organisation links")
      .authRoTransaction(db) { implicit request => implicit graph =>
        val isInDefaultOrganisation = userSrv.current.organisations.get(OrganisationSrv.administration.name).exists()
        val organisation =
          if (isInDefaultOrganisation)
            organisationSrv.get(organisationId)
          else
            userSrv
              .current
              .organisations
              .get(organisationId)

        Success(
          Results.Ok(
            JsArray(
              organisation
                .links
                .toList
                .map(_.toJson)
            )
          )
        )
      }
}
