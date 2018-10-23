package org.thp.thehive.controllers.v1
import play.api.mvc.{ Action, AnyContent, Results }

import javax.inject.{ Inject, Singleton }
import org.thp.scalligraph.controllers.ApiMethod
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.AuditSrv

@Singleton
class AuditCtrl @Inject()(apiMethod: ApiMethod, db: Database, auditSrv: AuditSrv) {
  def flow(): Action[AnyContent] = apiMethod("audit flow")
    .requires() { implicit request =>
//    auditSrv
      Results.Ok("")
    }

}
