package org.thp.thehive.connector.cortex.services

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.EventSrv
import org.thp.thehive.connector.cortex.models.{Action, AnalyzerTemplate, Job}
import org.thp.thehive.models.Observable
import org.thp.thehive.services.notification.NotificationTag
import org.thp.thehive.services.{AuditSrv, UserSrv}

class CortexAuditSrv(
    userSrv: => UserSrv,
    notificationActor: ActorRef @@ NotificationTag,
    eventSrv: EventSrv,
    db: Database
) extends AuditSrv(userSrv, notificationActor, eventSrv, db) {

  val job              = new ObjectAudit[Job, Observable]
  val action           = new ObjectAudit[Action, Product with Entity]
  val analyzerTemplate = new SelfContextObjectAudit[AnalyzerTemplate]

}
