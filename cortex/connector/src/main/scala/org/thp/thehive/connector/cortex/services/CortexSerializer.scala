package org.thp.thehive.connector.cortex.services

import akka.serialization.Serializer
import org.thp.cortex.dto.v0.OutputJob
import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.auth.{AuthContext, AuthContextImpl, Permission}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.io.NotSerializableException

object CortexSerializer {
  implicit val authContextReads: Reads[AuthContext] =
    ((JsPath \ "userId").read[String] and
      (JsPath \ "userName").read[String] and
      (JsPath \ "organisation").read[String].map(EntityIdOrName.apply) and
      (JsPath \ "requestId").read[String] and
      (JsPath \ "permissions").read[Set[String]].map(Permission.apply))(AuthContextImpl.apply _)

  implicit val authContextWrites: Writes[AuthContext] = Writes[AuthContext] { authContext =>
    Json.obj(
      "userId"       -> authContext.userId,
      "userName"     -> authContext.userName,
      "organisation" -> authContext.organisation.toString,
      "requestId"    -> authContext.requestId,
      "permissions"  -> authContext.permissions
    )
  }
  implicit val format: OFormat[CheckJob] = Json.format[CheckJob]
}

class CortexSerializer extends Serializer {
  import CortexSerializer._
  override def identifier: Int = -414525848

  override def includeManifest: Boolean = false

  override def toBinary(o: AnyRef): Array[Byte] =
    o match {
      case CheckJobs      => Array(0)
      case FirstCheckJobs => Array(1)
      case RemoteJob(job) => 2.toByte +: Json.toJson(job).toString.getBytes
      case cj: CheckJob   => 3.toByte +: Json.toJson(cj).toString().getBytes
      case _              => throw new NotSerializableException
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    bytes(0) match {
      case 0 => CheckJobs
      case 1 => FirstCheckJobs
      case 2 => RemoteJob(Json.parse(bytes.tail).as[OutputJob])
      case 3 => Json.parse(bytes.tail).as[CheckJob]
      case _ => throw new NotSerializableException
    }
}
