package models

import java.util.Date
import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future
import scala.util.Try

import play.api.Logger
import play.api.libs.json._

import models.JsonFormat.alertStatusFormat
import services.AuditedModel

import org.elastic4play.controllers.JsonInputValue
import org.elastic4play.models.{ Attribute, AttributeDef, BaseEntity, EntityDef, HiveEnumeration, ModelDef, MultiAttributeFormat, OptionalAttributeFormat, AttributeFormat ⇒ F, AttributeOption ⇒ O }
import org.elastic4play.services.DBLists
import org.elastic4play.utils.Hasher
import org.elastic4play.{ AttributeCheckingError, InvalidFormatAttributeError }

object AlertStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val New, Updated, Ignored, Imported = Value
}

trait AlertAttributes {
  _: AttributeDef ⇒
  val artifactAttributes: Seq[Attribute[_]] = {
    val remoteAttachmentAttributes = Seq(
      Attribute("alert", "reference", F.stringFmt, Nil, None, ""),
      Attribute("alert", "filename", OptionalAttributeFormat(F.stringFmt), Nil, None, ""),
      Attribute("alert", "contentType", OptionalAttributeFormat(F.stringFmt), Nil, None, ""),
      Attribute("alert", "size", OptionalAttributeFormat(F.numberFmt), Nil, None, ""),
      Attribute("alert", "hash", MultiAttributeFormat(F.stringFmt), Nil, None, ""),
      Attribute("alert", "type", OptionalAttributeFormat(F.stringFmt), Nil, None, ""))

    Seq(
      Attribute("alert", "data", OptionalAttributeFormat(F.stringFmt), Nil, None, ""),
      Attribute("alert", "dataType", F.stringFmt, Nil, None, ""),
      Attribute("alert", "message", OptionalAttributeFormat(F.stringFmt), Nil, None, ""),
      Attribute("alert", "startDate", OptionalAttributeFormat(F.dateFmt), Nil, None, ""),
      Attribute("alert", "attachment", OptionalAttributeFormat(F.attachmentFmt), Nil, None, ""),
      Attribute("alert", "remoteAttachment", OptionalAttributeFormat(F.objectFmt(remoteAttachmentAttributes)), Nil, None, ""),
      Attribute("alert", "tlp", OptionalAttributeFormat(TlpAttributeFormat), Nil, None, ""),
      Attribute("alert", "tags", MultiAttributeFormat(F.stringFmt), Nil, None, ""),
      Attribute("alert", "ioc", OptionalAttributeFormat(F.booleanFmt), Nil, None, ""))
  }

  val alertId: A[String] = attribute("_id", F.stringFmt, "Alert id", O.readonly)
  val tpe: A[String] = attribute("type", F.stringFmt, "Type of the alert", O.readonly)
  val source: A[String] = attribute("source", F.stringFmt, "Source of the alert", O.readonly)
  val sourceRef: A[String] = attribute("sourceRef", F.stringFmt, "Source reference of the alert", O.readonly)
  val date: A[Date] = attribute("date", F.dateFmt, "Date of the alert", new Date(), O.readonly)
  val lastSyncDate: A[Date] = attribute("lastSyncDate", F.dateFmt, "Date of the last synchronization", new Date())
  val caze: A[Option[String]] = optionalAttribute("case", F.stringFmt, "Id of the case, if created")
  val title: A[String] = attribute("title", F.textFmt, "Title of the alert")
  val description: A[String] = attribute("description", F.textFmt, "Description of the alert")
  val severity: A[Long] = attribute("severity", SeverityAttributeFormat, "Severity if the alert (0-3)", 2L)
  val tags: A[Seq[String]] = multiAttribute("tags", F.stringFmt, "Alert tags")
  val tlp: A[Long] = attribute("tlp", TlpAttributeFormat, "TLP level", 2L)
  val artifacts: A[Seq[JsObject]] = multiAttribute("artifacts", F.objectFmt(artifactAttributes), "Artifact of the alert", O.unaudited)
  val caseTemplate: A[Option[String]] = optionalAttribute("caseTemplate", F.stringFmt, "Case template to use")
  val status: A[AlertStatus.Value] = attribute("status", F.enumFmt(AlertStatus), "Status of the alert", AlertStatus.New)
  val follow: A[Boolean] = attribute("follow", F.booleanFmt, "", true)
}

@Singleton
class AlertModel @Inject() (dblists: DBLists)
  extends ModelDef[AlertModel, Alert]("alert", "Alert", "/alert")
  with AlertAttributes
  with AuditedModel {

  private[AlertModel] lazy val logger = Logger(getClass)
  override val defaultSortBy: Seq[String] = Seq("-date")
  override val removeAttribute: JsObject = Json.obj("status" → AlertStatus.Ignored)
  override val computedMetrics: Map[String, String] = Map(
    "observableCount" → "_source['artifacts']?.size()")

  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = {
    // check if data attribute is present on all artifacts
    val missingDataErrors = (attrs \ "artifacts")
      .asOpt[Seq[JsValue]]
      .getOrElse(Nil)
      .filter { a ⇒
        ((a \ "data").toOption.isEmpty && (a \ "attachment").toOption.isEmpty && (a \ "remoteAttachment").toOption.isEmpty) ||
          ((a \ "tags").toOption.isEmpty && (a \ "message").toOption.isEmpty)
      }
      .map(v ⇒ InvalidFormatAttributeError("artifacts", "artifact", JsonInputValue(v)))
    if (missingDataErrors.nonEmpty)
      Future.failed(AttributeCheckingError("alert", missingDataErrors))
    else
      Future.successful {
        if (attrs.keys.contains("_id"))
          attrs
        else {
          val hasher = Hasher("MD5")
          val tpe = (attrs \ "type").asOpt[String].getOrElse("<null>")
          val source = (attrs \ "source").asOpt[String].getOrElse("<null>")
          val sourceRef = (attrs \ "sourceRef").asOpt[String].getOrElse("<null>")
          val _id = hasher.fromString(s"$tpe|$source|$sourceRef").head.toString()
          attrs + ("_id" → JsString(_id))
        }
      }
  }
}

class Alert(model: AlertModel, attributes: JsObject)
  extends EntityDef[AlertModel, Alert](model, attributes)
  with AlertAttributes {

  override def toJson: JsObject = super.toJson +
    ("artifacts" → JsArray(artifacts().map {
      // for file artifact, parse data as Json
      case a if (a \ "dataType").asOpt[String].contains("file") ⇒
        Try(a + ("data" → Json.parse((a \ "data").as[String]))).getOrElse(a)
      case a ⇒ a
    }))

  def toCaseJson: JsObject = Json.obj(
    //"caseId" -> caseId,
    "title" → title(),
    "description" → description(),
    "severity" → severity(),
    //"owner" -> owner,
    "startDate" → date(),
    "tags" → tags(),
    "tlp" → tlp(),
    "status" → CaseStatus.Open)
}
