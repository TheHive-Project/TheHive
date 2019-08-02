package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.thehive.dto.v1.InputOrganisation
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{OrganisationSrv, UserSrv}
import play.api.http.HttpErrorHandler
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class OrganisationCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    organisationSrv: OrganisationSrv,
    errorHandler: HttpErrorHandler,
    userSrv: UserSrv
) {

  import OrganisationConversion._

  def create: Action[AnyContent] =
    entryPoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputOrganisation = request.body("organisation")
        for {
          _            <- userSrv.current.organisations(Permissions.manageOrganisation).get("default").existsOrFail()
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
