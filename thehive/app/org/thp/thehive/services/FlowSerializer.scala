package org.thp.thehive.services

import akka.serialization.Serializer
import org.thp.scalligraph.auth.{AuthContextImpl, Permission}
import org.thp.scalligraph.{EntityId, EntityIdOrName}
import play.api.libs.json._

import java.io.NotSerializableException

class FlowSerializer extends Serializer {
  override def identifier: Int = -1165729876

  override def includeManifest: Boolean = false

  def readFlowId(input: String): FlowId = {
    val json = Json.parse(input)
    FlowId((json \ "caseId").asOpt[String].map(EntityIdOrName.apply))(
      AuthContextImpl(
        (json \ "userId").as[String],
        (json \ "userName").as[String],
        EntityIdOrName((json \ "organisation").as[String]),
        (json \ "requestId").as[String],
        (json \ "permissions").as[Set[String]].map(Permission.apply)
      )
    )
  }
  def writeFlowId(flowId: FlowId): JsObject =
    Json.obj(
      "caseId"       -> flowId.caseId.fold[JsValue](JsNull)(c => JsString(c.toString)),
      "userId"       -> flowId.authContext.userId,
      "userName"     -> flowId.authContext.userName,
      "organisation" -> flowId.authContext.organisation.toString,
      "requestId"    -> flowId.authContext.requestId,
      "permissions"  -> flowId.authContext.permissions
    )

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case f: FlowId     => 0.toByte +: writeFlowId(f).toString().getBytes
      case AuditIds(ids) => 1.toByte +: ids.map(_.value).mkString("|").getBytes
      case _             => throw new NotSerializableException
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => readFlowId(new String(bytes.tail))
      case 1 => AuditIds(new String(bytes.tail).split('|').toSeq.map(EntityId.apply))
      case _ => throw new NotSerializableException
    }
}
