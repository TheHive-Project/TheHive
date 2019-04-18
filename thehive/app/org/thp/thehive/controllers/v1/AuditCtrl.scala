package org.thp.thehive.controllers.v1

import scala.util.Success

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.AuditSrv
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

@Singleton
class AuditCtrl @Inject()(entryPoint: EntryPoint, db: Database, auditSrv: AuditSrv) extends AuditConversion {
  def flow(): Action[AnyContent] =
    entryPoint("audit flow")
      .authTransaction(db) { _ ⇒ implicit graph ⇒
        val audits = auditSrv.initSteps.list.toList.map(_.toJson)
        Success(Results.Ok(JsArray(audits)))
      }
}
