package org.thp.thehive.migration.th4

import akka.actor.ActorRef
import com.google.inject.name.Named
import javax.inject.{Inject, Provider, Singleton}
import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.EventSrv
import org.thp.thehive.models.Audit
import org.thp.thehive.services.{AuditSrv, UserSrv}

import scala.util.{Success, Try}

@Singleton
class NoAuditSrv @Inject() (
    userSrvProvider: Provider[UserSrv],
    @Named("notification-actor") notificationActor: ActorRef,
    eventSrv: EventSrv
)(implicit @Named("with-thehive-schema") db: Database)
    extends AuditSrv(userSrvProvider, notificationActor, eventSrv)(db) {

  override def create(audit: Audit, context: Option[Product with Entity], `object`: Option[Product with Entity])(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Unit] =
    Success(())

  override def mergeAudits[R](body: => Try[R])(auditCreator: R => Try[Unit])(implicit graph: Graph): Try[R] = body
}
