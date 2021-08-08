package org.thp.thehive.services

import akka.actor.ExtendedActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRefResolver, Behavior, ActorRef => TypedActorRef}
import akka.serialization.Serializer
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps

import java.io.NotSerializableException
import java.nio.ByteBuffer

object CaseNumberActor extends TraversalOps with TheHiveOpsNoDeps {
  sealed trait Message
  sealed trait Request                                       extends Message
  sealed trait Response                                      extends Message
  case class GetNextNumber(replyTo: TypedActorRef[Response]) extends Request
  case class NextNumber(number: Int)                         extends Response

  def behavior(db: Database, caseSrv: CaseSrv): Behavior[Request] = {
    val nextNumber = db.roTransaction { implicit graph =>
      caseSrv.startTraversal.getLast.headOption.fold(0)(_.number) + 1
    }
    caseNumberProvider(nextNumber)
  }

  def caseNumberProvider(nextNumber: Int): Behavior[Request] =
    Behaviors.receiveMessage {
      case GetNextNumber(replyTo) =>
        replyTo ! NextNumber(nextNumber)
        caseNumberProvider(nextNumber + 1)
    }
}

class CaseNumberSerializer(system: ExtendedActorSystem) extends Serializer {
  import CaseNumberActor._

  private val actorRefResolver = ActorRefResolver(system.toTyped)

  override def identifier: Int = 9739323

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case GetNextNumber(replyTo) => 0.toByte +: actorRefResolver.toSerializationFormat(replyTo).getBytes
      case NextNumber(number)     => ByteBuffer.allocate(5).put(1.toByte).putInt(number).array()
      case _                      => throw new NotSerializableException
    }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => GetNextNumber(actorRefResolver.resolveActorRef(new String(bytes.tail)))
      case 1 => NextNumber(ByteBuffer.wrap(bytes).getInt(1))
    }
}
