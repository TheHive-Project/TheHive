package org.thp.thehive.connector.cortex.models

import akka.serialization.Serializer

import java.io.NotSerializableException

class SchemaUpdaterSerializer extends Serializer {
  override def identifier: Int = -639734235

  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case RequestDB => Array(0)
      case DBReady   => Array(1)
      case _         => throw new NotSerializableException
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => RequestDB
      case 1 => DBReady
      case _ => throw new NotSerializableException
    }
}
