package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.ApiMethod
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.AuditSrv
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class AuditCtrl @Inject()(apiMethod: ApiMethod, db: Database, auditSrv: AuditSrv) {
  def flow(): Action[AnyContent] =
    apiMethod("audit flow")
      .requires() { _ ⇒
        db.transaction { implicit graph ⇒
          val audits = auditSrv.initSteps.list.toList.map(_.toJson)
          Results.Ok(JsArray(audits))
        }
      }
}
