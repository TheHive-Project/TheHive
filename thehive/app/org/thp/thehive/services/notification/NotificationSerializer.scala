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
      case m: NotificationExecution    => Json.toBytes(Json.toJson(m))
      case m: AuditNotificationMessage => Json.toBytes(Json.toJson(m))
      case _                           => Array.empty[Byte] // Not serializable
    }

  /**
    * Produces an object from an array of bytes, with an optional type-hint;
    * the class should be loaded using ActorSystem.dynamicAccess.
    */
  @throws(classOf[NotSerializableException])
  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    manifest
      .flatMap {
        case c if c == classOf[NotificationExecution]    => Json.parse(bytes).asOpt[NotificationExecution]
        case c if c == classOf[AuditNotificationMessage] => Json.parse(bytes).asOpt[AuditNotificationMessage]
        case _                                           => None
      }
      .getOrElse(throw new NotSerializableException)
}
