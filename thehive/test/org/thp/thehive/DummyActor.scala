package org.thp.thehive

import akka.actor.Actor

class DummyActor extends Actor {
  override def receive: Receive = PartialFunction.empty
}
