package org.thp.thehive.controllers.v1

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import io.scalaland.chimney.dsl._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{ApiMethod, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models._
import org.thp.thehive.services.OrganisationSrv

object OrganisationXfrm {
  def fromInput(inputOrganisation: InputOrganisation): Organisation =
    inputOrganisation
      .into[Organisation]
      .transform

  def toOutput(organisation: Organisation): OutputOrganisation =
    organisation
      .into[OutputOrganisation]
      .transform
}

@Singleton
class OrganisationCtrl @Inject()(apiMethod: ApiMethod, db: Database, organisationSrv: OrganisationSrv) {

  def create: Action[AnyContent] =
    apiMethod("create organisation")
      .extract('organisation, FieldsParser[InputOrganisation])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val inputOrganisation: InputOrganisation = request.body('organisation)
          val createdOrganisation                  = organisationSrv.create(OrganisationXfrm.fromInput(inputOrganisation))
          val outputOrganisation                   = OrganisationXfrm.toOutput(createdOrganisation)
          Results.Created(Json.toJson(outputOrganisation))
        }
      }

  def get(organisationId: String): Action[AnyContent] =
    apiMethod("get organisation")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val organisation = organisationSrv
            .getOrFail(organisationId)
          val outputOrganisation = OrganisationXfrm.toOutput(organisation)
          Results.Ok(Json.toJson(outputOrganisation))
        }
      }

  def list: Action[AnyContent] =
    apiMethod("list organisation")
      .requires(Permissions.read) { implicit request ⇒
        db.transaction { implicit graph ⇒
          val organisations = organisationSrv.initSteps.toList
            .map(OrganisationXfrm.toOutput)
          Results.Ok(Json.toJson(organisations))
        }
      }

  def update(organisationId: String): Action[AnyContent] =
    apiMethod("update organisation")
      .extract('organisation, UpdateFieldsParser[InputOrganisation])
      .requires(Permissions.admin) { implicit request ⇒
        db.transaction { implicit graph ⇒
          organisationSrv.update(organisationId, request.body('organisation))
          Results.NoContent
        }
      }
}
