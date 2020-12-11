package org.thp.thehive.connector.misp.services

import akka.serialization.Serializer

class MispSerializer extends Serializer {
  override def identifier: Int = -222314660

  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case Synchro                   => Array(0)
      case EndOfSynchro(None)        => Array(1)
      case EndOfSynchro(Some(error)) => 2.toByte +: error.getBytes()
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => Synchro
      case 1 => EndOfSynchro(None)
      case 2 => EndOfSynchro(Some(new String(bytes.tail)))
    }
}
