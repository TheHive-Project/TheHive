package org.thp.thehive.services

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorRefResolver
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.serialization.Serializer
import play.api.libs.json.{Json, OFormat}

import java.io.NotSerializableException

class IntegrityCheckSerializer(system: ExtendedActorSystem) extends Serializer {
  import IntegrityCheck._
  override def identifier: Int = -604584588

  override def includeManifest: Boolean = false
  implicit class RichBoolean(b: Boolean) {
    def toByte: Byte = if (b) 1.toByte else 0.toByte
  }
  implicit class RichByte(b: Byte) {
    def toBoolean: Boolean = if (b == 0) false else true
  }
  implicit val finishDedupFormat: OFormat[FinishDedup]   = Json.format[FinishDedup]
  implicit val finishGlobalFormat: OFormat[FinishGlobal] = Json.format[FinishGlobal]
  private val actorRefResolver                           = ActorRefResolver(system.toTyped)

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case EntityAdded(name)                 => 0.toByte +: name.getBytes
      case NeedCheck(name)                   => 1.toByte +: name.getBytes
      case CheckRequest(name, dedup, global) => Array(2.toByte, dedup.toByte, global.toByte) ++ name.getBytes
      case GetAllCheckStats(replyTo)         => 3.toByte +: actorRefResolver.toSerializationFormat(replyTo).getBytes
      case AllCheckStats(map)                => 4.toByte +: Json.toJson(map).toString.getBytes
      case StartDedup(name: String)          => 5.toByte +: name.getBytes
      case fd: FinishDedup                   => 6.toByte +: Json.toJson(fd).toString.getBytes
      case StartGlobal(name: String)         => 7.toByte +: name.getBytes
      case fg: FinishGlobal                  => 8.toByte +: Json.toJson(fg).toString.getBytes
      case CancelCheck                       => Array(9.toByte)
      case _                                 => throw new NotSerializableException
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => EntityAdded(new String(bytes.tail))
      case 1 => NeedCheck(new String(bytes.tail))
      case 2 => CheckRequest(new String(bytes.drop(3)), bytes(1).toBoolean, bytes(2).toBoolean)
      case 3 => GetAllCheckStats(actorRefResolver.resolveActorRef(new String(bytes.tail)))
      case 4 => AllCheckStats(Json.parse(bytes.tail).as[Map[String, Map[String, Long]]])
      case 5 => StartDedup(new String(bytes.tail))
      case 6 => Json.parse(bytes.tail).as[FinishDedup]
      case 7 => StartGlobal(new String(bytes.tail))
      case 8 => Json.parse(bytes.tail).as[FinishGlobal]
      case 9 => CancelCheck
      case _ => throw new NotSerializableException
    }
}
