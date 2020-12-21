package org.thp.thehive.services

import akka.serialization.Serializer
import org.thp.scalligraph.{EntityId, EntityIdOrName}

import java.io.NotSerializableException

class FlowSerializer extends Serializer {
  override def identifier: Int = -1165729876

  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case FlowId(organisation, None)         => 0.toByte +: organisation.toString.getBytes
      case FlowId(organisation, Some(caseId)) => 1.toByte +: s"$organisation|$caseId".getBytes
      case AuditIds(ids)                      => 2.toByte +: ids.map(_.value).mkString("|").getBytes
      case _                                  => throw new NotSerializableException
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => FlowId(EntityIdOrName(new String(bytes.tail)), None)
      case 1 =>
        new String(bytes.tail).split('|') match {
          case Array(organisation, caseId) => FlowId(EntityIdOrName(organisation), Some(EntityIdOrName(caseId)))
          case _                           => throw new NotSerializableException
        }
      case 2 => AuditIds(new String(bytes.tail).split('|').toSeq.map(EntityId.apply))
      case _ => throw new NotSerializableException
    }
}
