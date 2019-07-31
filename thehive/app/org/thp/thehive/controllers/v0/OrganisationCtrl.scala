package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.thehive.dto.v0.InputOrganisation
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{OrganisationSrv, UserSrv}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class OrganisationCtrl @Inject()(entryPoint: EntryPoint, db: Database, organisationSrv: OrganisationSrv, userSrv: UserSrv) {
  import OrganisationConversion._

  def create: Action[AnyContent] =
    entryPoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputOrganisation: InputOrganisation = request.body("organisation")
        val createdOrganisation                  = organisationSrv.create(fromInputOrganisation(inputOrganisation))
        val outputOrganisation                   = toOutputOrganisation(createdOrganisation)
        Success(Results.Created(Json.toJson(outputOrganisation)))
      }

  def get(organisationId: String): Action[AnyContent] =
    entryPoint("get organisation")
      .authTransaction(db) { _ => implicit graph =>
        organisationSrv
          .getOrFail(organisationId)
          .map { organisation =>
            val outputOrganisation = toOutputOrganisation(organisation)
            Results.Ok(Json.toJson(outputOrganisation))
          }
      }

  def list: Action[AnyContent] =
    entryPoint("list organisation")
      .authTransaction(db) { _ => implicit graph =>
        val organisations = organisationSrv
          .initSteps
          .toList
          .map(toOutputOrganisation)
        Success(Results.Ok(Json.toJson(organisations)))
      }

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
