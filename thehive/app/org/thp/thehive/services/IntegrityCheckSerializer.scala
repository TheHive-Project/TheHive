package org.thp.thehive.services

import akka.serialization.Serializer
import play.api.libs.json.{Json, OFormat}

import java.io.NotSerializableException

class IntegrityCheckSerializer extends Serializer {
  override def identifier: Int = -604584588

  override def includeManifest: Boolean = false

  implicit val duplicationCheckResultFormat: OFormat[DuplicationCheckResult] = Json.format[DuplicationCheckResult]
  implicit val globalCheckResultFormat: OFormat[GlobalCheckResult]           = Json.format[GlobalCheckResult]

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case EntityAdded(name)                              => 0.toByte +: name.getBytes
      case NeedCheck(name)                                => 1.toByte +: name.getBytes
      case DuplicationCheck(name)                         => 2.toByte +: name.getBytes
      case duplicationCheckResult: DuplicationCheckResult => 3.toByte +: Json.toJson(duplicationCheckResult).toString.getBytes
      case GlobalCheckRequest(name)                       => 4.toByte +: name.getBytes
      case globalCheckResult: GlobalCheckResult           => 5.toByte +: Json.toJson(globalCheckResult).toString.getBytes
      case GetCheckStats(name)                            => 6.toByte +: name.getBytes
      case _                                              => throw new NotSerializableException
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => EntityAdded(new String(bytes.tail))
      case 1 => NeedCheck(new String(bytes.tail))
      case 2 => DuplicationCheck(new String(bytes.tail))
      case 3 => Json.parse(bytes.tail).as[DuplicationCheckResult]
      case 4 => GlobalCheckRequest(new String(bytes.tail))
      case 5 => Json.parse(bytes.tail).as[GlobalCheckResult]
      case 6 => GetCheckStats(new String(bytes.tail))
      case _ => throw new NotSerializableException
    }
}
