package org.thp.thehive.services

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import org.thp.thehive.TestAppBuilder
import play.api.test.PlaySpecification

class CaseNumberTest extends PlaySpecification with TestAppBuilder {

  "case number actor" should {
    "serialize and deserialize messages" in withActorSystem { system =>
      val ref = system.deadLetters[CaseNumberActor.Response]
      val sut = new CaseNumberSerializer(system.classicSystem.asInstanceOf[ExtendedActorSystem])

      val messages = Seq(
        CaseNumberActor.GetNextNumber(ref),
        CaseNumberActor.NextNumber(42),
        CaseNumberActor.NextNumber(Int.MaxValue)
      )
      val out = messages.map(message => sut.toBinary(message))

      val result = out.map(bin => sut.fromBinary(bin))

      result must beEqualTo(messages)
    }
  }

  private def withActorSystem[T](body: ActorSystem[Nothing] => T) = {
    val system = ActorSystem(Behaviors.empty, "test")
    try body(system)
    finally system.terminate()
  }
}
