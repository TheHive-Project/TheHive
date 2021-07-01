package org.thp.thehive.services

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRefResolver, Behavior, ActorRef => TypedActorRef}
import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.serialization.Serializer
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.GuiceAkkaExtension
import org.thp.thehive.services.CaseOps._

import java.io.NotSerializableException
import java.nio.ByteBuffer
import javax.inject.{Inject, Provider, Singleton}

object CaseNumberActor {
  sealed trait Message
  sealed trait Request                                       extends Message
  sealed trait Response                                      extends Message
  case class GetNextNumber(replyTo: TypedActorRef[Response]) extends Request
  case class NextNumber(number: Int)                         extends Response

  val behavior: Behavior[Request] = Behaviors.setup[Request](context => waitFirstRequest(context))

  def getNextCaseNumber(context: ActorContext[Request]): Int = {
    val injector = GuiceAkkaExtension(context.system).injector
    val db       = injector.getInstance(classOf[Database])
    val caseSrv  = injector.getInstance(classOf[CaseSrv])
    db.roTransaction { implicit graph =>
      caseSrv.startTraversal.getLast.headOption.fold(0)(_.number) + 1
    }
  }

  def waitFirstRequest(context: ActorContext[Request]): Behaviors.Receive[Request] =
    Behaviors.receiveMessage {
      case GetNextNumber(replyTo) =>
        val nextNumber = getNextCaseNumber(context)
        replyTo ! NextNumber(nextNumber)
        caseNumberProvider(nextNumber + 1)
    }

  def caseNumberProvider(nextNumber: Int): Behavior[Request] =
    Behaviors.receiveMessage {
      case GetNextNumber(replyTo) =>
        replyTo ! NextNumber(nextNumber)
        caseNumberProvider(nextNumber + 1)
    }
}

@Singleton
class CaseNumberActorProvider @Inject() (system: ActorSystem) extends Provider[TypedActorRef[CaseNumberActor.Request]] {
  override lazy val get: TypedActorRef[CaseNumberActor.Request] =
    ClusterSingleton(system.toTyped)
      .init(SingletonActor(CaseNumberActor.behavior, "CaseNumberLeader"))
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
