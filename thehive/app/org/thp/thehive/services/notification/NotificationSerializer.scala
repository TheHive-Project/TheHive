package org.thp.thehive.services.notification

import java.io.NotSerializableException

import akka.serialization.Serializer
import play.api.libs.json.Json

class NotificationSerializer extends Serializer {
  override def identifier: Int = 226591536

  override def includeManifest: Boolean = false

  /**
    * Serializes the given object into an Array of Byte
    */
  def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case m: NotificationExecution    => 0.toByte +: Json.toBytes(Json.toJson(m))
      case m: AuditNotificationMessage => 1.toByte +: Json.toBytes(Json.toJson(m))
      case _                           => throw new NotSerializableException
    }

  /**
    * Produces an object from an array of bytes, with an optional type-hint;
    * the class should be loaded using ActorSystem.dynamicAccess.
    */
  @throws(classOf[NotSerializableException])
  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => Json.parse(bytes.tail).asOpt[NotificationExecution]
      case 1 => Json.parse(bytes.tail).asOpt[AuditNotificationMessage]
      case _ => throw new NotSerializableException
    }
}
