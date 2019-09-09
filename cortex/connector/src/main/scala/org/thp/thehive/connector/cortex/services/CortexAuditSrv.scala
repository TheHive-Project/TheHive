package org.thp.thehive.connector.cortex.services

import akka.actor.ActorRef
import com.google.inject.Singleton
import com.google.inject.name.Named
import javax.inject.{Inject, Provider}
import org.thp.scalligraph.models.{Database, Entity, Schema}
import org.thp.scalligraph.services.EventSrv
import org.thp.thehive.connector.cortex.models.{Action, Job, ReportTemplate}
import org.thp.thehive.models.Case
import org.thp.thehive.services.{AuditSrv, UserSrv}

@Singleton
class CortexAuditSrv @Inject()(
    userSrvProvider: Provider[UserSrv],
    @Named("notification-actor") notificationActor: ActorRef,
    eventSrv: EventSrv
)(implicit db: Database, schema: Schema)
    extends AuditSrv(userSrvProvider, notificationActor, eventSrv) {

  val job            = new ObjectAudit[Job, Case]
  val action         = new ObjectAudit[Action, Entity]
  val reportTemplate = new SelfContextObjectAudit[ReportTemplate]

}
