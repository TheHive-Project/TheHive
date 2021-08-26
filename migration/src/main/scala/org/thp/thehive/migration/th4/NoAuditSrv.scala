package org.thp.thehive.migration.th4

import akka.actor.ActorRef
import com.softwaremill.tagging.@@
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.EventSrv
import org.thp.scalligraph.traversal.Graph
import org.thp.thehive.connector.cortex.services.CortexAuditSrv
import org.thp.thehive.models.Audit
import org.thp.thehive.services.UserSrv
import org.thp.thehive.services.notification.NotificationTag

import scala.util.{Success, Try}

class NoAuditSrv(
    userSrv: => UserSrv,
    notificationActor: ActorRef @@ NotificationTag,
    eventSrv: EventSrv,
    db: Database
) extends CortexAuditSrv(userSrv, notificationActor, eventSrv, db) {

  override def create(audit: Audit, context: Product with Entity, `object`: Option[Product with Entity])(implicit
      graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    Success(())

  override def mergeAudits[R](body: => Try[R])(auditCreator: R => Try[Unit])(implicit graph: Graph): Try[R] = body
}
