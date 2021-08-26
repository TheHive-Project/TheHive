package org.thp.thehive.services

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRefResolver, Behavior, ActorRef => TypedActorRef}
import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.serialization.Serializer
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.services.config.ApplicationConfig
import org.thp.scalligraph.services.config.ApplicationConfig.finiteDurationFormat
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.thehive.services.CaseOps._

import java.io.NotSerializableException
import java.nio.ByteBuffer
import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.duration.FiniteDuration

object CaseNumberActor {
  sealed trait Message
  sealed trait Request                                       extends Message
  sealed trait Response                                      extends Message
  case class GetNextNumber(replyTo: TypedActorRef[Response]) extends Request
  case class NextNumber(number: Int)                         extends Response
  case object ReloadFromDatabase                             extends Request

  def behavior(db: Database, appConfig: ApplicationConfig, caseSrvProvider: Provider[CaseSrv]): Behavior[Request] = {
    lazy val caseSrv: CaseSrv = caseSrvProvider.get
    val caseNumberReloadIntervalConfig =
      appConfig.item[FiniteDuration]("caseNumber.reload.interval", "Reload last case number from database interval")
    Behaviors.withTimers { timers =>
      val reloadTimer: () => Unit = () => timers.startSingleTimer(ReloadFromDatabase, caseNumberReloadIntervalConfig.get)
      val getNextNumber: () => Int = () =>
        db.roTransaction { implicit graph =>
          caseSrv.startTraversal.getLast.headOption.fold(0)(_.number) + 1
        }
      caseNumberProvider(getNextNumber, reloadTimer, getNextNumber())
    }
  }

  def caseNumberProvider(getNextNumber: () => Int, reloadTimer: () => Unit, nextNumber: Int): Behavior[Request] =
    Behaviors.receiveMessage {
      case GetNextNumber(replyTo) =>
        replyTo ! NextNumber(nextNumber)
        caseNumberProvider(getNextNumber, reloadTimer, nextNumber + 1)
      case ReloadFromDatabase =>
        reloadTimer()
        caseNumberProvider(getNextNumber, reloadTimer, Math.max(getNextNumber(), nextNumber))
    }
}

@Singleton
class CaseNumberActorProvider @Inject() (system: ActorSystem, db: Database, appConfig: ApplicationConfig, caseSrvProvider: Provider[CaseSrv])
    extends Provider[TypedActorRef[CaseNumberActor.Request]] {
  override lazy val get: TypedActorRef[CaseNumberActor.Request] =
    ClusterSingleton(system.toTyped)
      .init(SingletonActor(CaseNumberActor.behavior(db, appConfig, caseSrvProvider), "CaseNumberLeader"))
}

class CaseNumberSerializer(system: ExtendedActorSystem) extends Serializer {
  import CaseNumberActor._

  private val actorRefResolver = ActorRefResolver(system.toTyped)

  override def identifier: Int = 9739323

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case GetNextNumber(replyTo) => 0.toByte +: actorRefResolver.toSerializationFormat(replyTo).getBytes
      case NextNumber(number)     => ByteBuffer.allocate(5).put(1.toByte).putInt(number).array()
      case ReloadFromDatabase     => Array(2)
      case _                      => throw new NotSerializableException
    }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => GetNextNumber(actorRefResolver.resolveActorRef(new String(bytes.tail)))
      case 1 => NextNumber(ByteBuffer.wrap(bytes).getInt(1))
      case 2 => ReloadFromDatabase
      case _ => throw new NotSerializableException
    }
}
