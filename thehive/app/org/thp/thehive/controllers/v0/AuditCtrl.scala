package org.thp.thehive.controllers.v0

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.EntryPoint
import org.thp.scalligraph.models.Database
import org.thp.thehive.services.AuditSrv
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Success

@Singleton
class AuditCtrl @Inject()(entryPoint: EntryPoint, db: Database, auditSrv: AuditSrv) extends AuditConversion {

  // TODO deal with rootId and double check the purpose of count
  def flow(rootId: Option[String], count: Option[Int]): Action[AnyContent] =
    entryPoint("audit flow")
      .authTransaction(db) { implicit request ⇒ implicit graph ⇒
        val audits = rootId
          .fold(auditSrv.initSteps)(rid ⇒ auditSrv.initSteps.forContext(rid))
          .visible
          .range(0, count.getOrElse(10))
          .richAudit
          .toList
          .map(_.toJson)
        Success(Results.Ok(JsArray(audits.take(count.getOrElse(10)))))
      }
}
