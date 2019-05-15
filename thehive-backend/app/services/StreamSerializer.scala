package services

import java.io.NotSerializableException

import scala.util.Try

import play.api.libs.json._

import akka.serialization.Serializer
import services.StreamActor.{GetOperations, StreamMessages, Submit}

class StreamSerializer extends Serializer {
  def identifier: Int = 226591535
  def includeManifest = false

  /**
    * Serializes the given object into an Array of Byte
    */
  def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case GetOperations       ⇒ "GetOperations".getBytes
      case StreamMessages(msg) ⇒ JsArray(msg).toString.getBytes
      case Submit              ⇒ "Submit".getBytes
      case _                   ⇒ Array.empty[Byte] // Not serializable
    }

  /**
    * Produces an object from an array of bytes, with an optional type-hint;
    * the class should be loaded using ActorSystem.dynamicAccess.
    */
  @throws(classOf[NotSerializableException])
  def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    new String(bytes) match {
      case "GetOperations" ⇒ GetOperations
      case "Submit"        ⇒ Submit
      case s               ⇒ Try(StreamMessages(Json.parse(s).as[Seq[JsObject]])).getOrElse(throw new NotSerializableException)
    }
}
