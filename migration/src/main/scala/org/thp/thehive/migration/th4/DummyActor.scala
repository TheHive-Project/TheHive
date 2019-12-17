package org.thp.thehive.migration.th4

import akka.actor.Actor

class DummyActor extends Actor {
  override def receive: Receive = PartialFunction.empty
}
