package org.thp.thehive.models

import akka.serialization.Serializer

import java.io.NotSerializableException

class SchemaUpdaterSerializer extends Serializer {
  override def identifier: Int = 272437668

  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case RequestDB(instanceId) => 0.toByte +: instanceId.getBytes
      case DBReady               => Array(1)
      case Update                => Array(2)
      case _                     => throw new NotSerializableException
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => RequestDB(new String(bytes.tail))
      case 1 => DBReady
      case 2 => Update
      case _ => throw new NotSerializableException
    }
}
