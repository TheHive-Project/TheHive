package org.thp.thehive.controllers.v0

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v0.{InputShare, OutputShare}
import org.thp.thehive.services.{CaseSrv, OrganisationSrv, ProfileSrv, ShareSrv}

@Singleton
class ShareCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    shareSrv: ShareSrv,
    organisationSrv: OrganisationSrv,
    caseSrv: CaseSrv,
    profileSrv: ProfileSrv
) {

  def shareCase: Action[AnyContent] =
    entryPoint("create case share")
      .extract("share", FieldsParser[InputShare])
      .authTransaction(db) { implicit request => implicit graph =>
        val inputShare: InputShare = request.body("share")
        for {
          organisation <- organisationSrv.getOrFail(inputShare.organisationName)
          case0        <- caseSrv.getOrFail(inputShare.caseId)
          profile      <- profileSrv.getOrFail(inputShare.profile)
          share        <- shareSrv.create(case0, organisation, profile)
          outputShare = OutputShare(
            share._id,
            share._id,
            share._createdBy,
            share._updatedBy,
            share._createdAt,
            share._updatedAt,
            "share",
            case0._id,
            organisation.name,
            profile.name
          )
        } yield Results.Created(Json.toJson(outputShare))
      }
}
