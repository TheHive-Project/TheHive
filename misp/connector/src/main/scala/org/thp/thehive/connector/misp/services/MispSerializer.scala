package org.thp.thehive.connector.misp.services

import akka.serialization.Serializer

import java.io.NotSerializableException

class MispSerializer extends Serializer {
  override def identifier: Int = -222314660

  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case Synchro => Array(0)
      case _       => throw new NotSerializableException
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => Synchro
      case _ => throw new NotSerializableException
    }
}
