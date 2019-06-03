package org.thp.thehive.services

import play.api.Logger

import akka.actor.ActorRef
import akka.event.{ActorEventBus, SubchannelClassification}
import akka.util.Subclassification
import javax.inject.Singleton

//import java.util.Date
//
//import gremlin.scala._
//import javax.inject.{Inject, Singleton}
//import org.apache.tinkerpop.gremlin.process.traversal.Order
//import org.thp.scalligraph.EntitySteps
//import org.thp.scalligraph.auth.AuthContext
//import org.thp.scalligraph.models._
//import org.thp.scalligraph.services._
//import org.thp.thehive.models.{Audit, AuditableAction, Audited, RichAudit}
//
//case class EventMessage()
//
//@Singleton
//class EventSrv @Inject()()(implicit db: Database, schema: Schema) extends VertexSrv[Audit, AuditSteps] {
//
//  def getObject(audit: Audit with Entity)(implicit graph: Graph): Option[Entity] =
//    get(audit).getObject
//
//  def create(action: AuditableAction.Value, entity: Entity, field: String, oldValue: String, newValue: String)(
//      implicit graph: Graph,
//      authContext: AuthContext
//  ): Audit with Entity = create(action, entity, Some(field), Some(oldValue), Some(newValue))
//
//  def create(
//      action: AuditableAction.Value,
//      context: Entity,
//      entity: Seq[Entity],
//      field: Option[String] = None,
//      details: Option[String] = None
//  )(
//      implicit graph: Graph,
//      authContext: AuthContext
//  ): Audit with Entity = {
//    val audit =
//      if (entity._model.label == "User" && field.contains("password"))
//        Audit(action, authContext.requestId, field, Some("**hidden**"), Some("**hidden**"))
//      else
//        Audit(action, authContext.requestId, field, oldValue, newValue)
//    val createdAudit = create(audit)
//    edgeSrv.create(Audited(), createdAudit, entity)
//    createdAudit
//  }
//}

trait EventMessage

@Singleton
class EventSrv extends ActorEventBus with SubchannelClassification {
  private[EventSrv] lazy val logger = Logger(getClass)
  override type Classifier = Class[_ <: EventMessage]
  override type Event      = EventMessage

  override protected def classify(event: EventMessage): Classifier                = event.getClass
  override protected def publish(event: EventMessage, subscriber: ActorRef): Unit = subscriber ! event

  implicit protected def subclassification: Subclassification[Classifier] = new Subclassification[Classifier] {
    def isEqual(x: Classifier, y: Classifier): Boolean    = x == y
    def isSubclass(x: Classifier, y: Classifier): Boolean = y.isAssignableFrom(x)
  }
}
