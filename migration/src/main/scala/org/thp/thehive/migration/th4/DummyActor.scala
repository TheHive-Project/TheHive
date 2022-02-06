package org.thp.thehive.migration.th4

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.{Actor, ActorSystem}
import akka.actor.typed.{ActorRef => TypedActorRef}

import java.util.UUID
import javax.inject.{Inject, Provider}

class DummyActor extends Actor {
  override def receive: Receive = PartialFunction.empty
}

class DummyTypedActorProvider[T] @Inject() (actorSystem: ActorSystem) extends Provider[TypedActorRef[T]] {
  override def get(): TypedActorRef[T] =
    actorSystem
      .toTyped
      .systemActorOf(Behaviors.empty, UUID.randomUUID().toString)
}
