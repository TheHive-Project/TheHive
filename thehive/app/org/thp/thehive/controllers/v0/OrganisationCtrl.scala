package org.thp.thehive.controllers.v0

import scala.util.Success

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v0.InputOrganisation
import org.thp.thehive.services.OrganisationSrv

@Singleton
class OrganisationCtrl @Inject()(entryPoint: EntryPoint, db: Database, organisationSrv: OrganisationSrv) extends OrganisationConversion {

  def create: Action[AnyContent] =
    entryPoint("create organisation")
      .extract('organisation, FieldsParser[InputOrganisation])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val inputOrganisation: InputOrganisation = request.body('organisation)
          val createdOrganisation                  = organisationSrv.create(fromInputOrganisation(inputOrganisation))
          val outputOrganisation                   = toOutputOrganisation(createdOrganisation)
          Success(Results.Created(Json.toJson(outputOrganisation)))
        }
      }

  def get(organisationId: String): Action[AnyContent] =
    entryPoint("get organisation")
      .authenticated { _ ⇒
        db.tryTransaction { implicit graph ⇒
          organisationSrv
            .getOrFail(organisationId)
            .map { organisation ⇒
              val outputOrganisation = toOutputOrganisation(organisation)
              Results.Ok(Json.toJson(outputOrganisation))
            }
        }
      }

  def list: Action[AnyContent] =
    entryPoint("list organisation")
      .authenticated { _ ⇒
        db.tryTransaction { implicit graph ⇒
          val organisations = organisationSrv.initSteps.toList
            .map(toOutputOrganisation)
          Success(Results.Ok(Json.toJson(organisations)))
        }
      }

  def update(organisationId: String): Action[AnyContent] =
    entryPoint("update organisation")
      .extract('organisation, UpdateFieldsParser[InputOrganisation])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          organisationSrv.update(organisationId, request.body('organisation))
          Success(Results.NoContent)
        }
      }
}
