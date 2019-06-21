package org.thp.thehive.services

import play.api.Logger

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, Unsubscribe}
import javax.inject.{Inject, Singleton}

object EventSrv {
  final val STREAM = "stream"
}

@Singleton
class EventSrv @Inject()(system: ActorSystem) {
  import EventSrv._
  lazy val logger      = Logger(getClass)
  private val mediator = DistributedPubSub(system).mediator

  def publish(event: StreamMessage): Unit = {
    logger.debug(s"publish stream $event")
    mediator ! Publish(STREAM, event)
  }
  def subscribe(actor: ActorRef): Unit   = mediator ! Subscribe(STREAM, actor)
  def unsubscribe(actor: ActorRef): Unit = mediator ! Unsubscribe(STREAM, actor)
}
