package org.thp.thehive.services

import akka.serialization.Serializer

import java.io.NotSerializableException

class IntegrityCheckSerializer extends Serializer {
  override def identifier: Int = -604584588

  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case EntityAdded(name) => 0.toByte +: name.getBytes
      case _                 => throw new NotSerializableException
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => EntityAdded(new String(bytes.tail))
      case _ => throw new NotSerializableException
    }
}
