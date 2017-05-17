package models

import java.util.Date
import javax.inject.{ Inject, Provider, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import akka.stream.Materializer
import play.api.libs.json.{ JsNull, JsObject, JsString, JsValue, JsArray }
import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import org.elastic4play.BadRequestError
import org.elastic4play.models.{ AttributeDef, BaseEntity, ChildModelDef, EntityDef, HiveEnumeration, AttributeFormat ⇒ F, AttributeOption ⇒ O }
import org.elastic4play.services.{ Attachment, DBLists }
import org.elastic4play.utils.MultiHash
import models.JsonFormat.artifactStatusFormat
import services.{ ArtifactSrv, AuditedModel }

object ArtifactStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Ok, Deleted = Value
}

trait ArtifactAttributes { _: AttributeDef ⇒
  def dblists: DBLists
  val artifactId: A[String] = attribute("_id", F.stringFmt, "Artifact id", O.model)
  val data: A[Option[String]] = optionalAttribute("data", F.stringFmt, "Content of the artifact", O.readonly)
  val dataType: A[String] = attribute("dataType", F.listEnumFmt("artifactDataType")(dblists), "Type of the artifact", O.readonly)
  val message: A[String] = attribute("message", F.textFmt, "Description of the artifact in the context of the case")
  val startDate: A[Date] = attribute("startDate", F.dateFmt, "Creation date", new Date)
  val attachment: A[Option[Attachment]] = optionalAttribute("attachment", F.attachmentFmt, "Artifact file content", O.readonly)
  val tlp: A[Long] = attribute("tlp", F.numberFmt, "TLP level", 2L)
  val tags: A[Seq[String]] = multiAttribute("tags", F.stringFmt, "Artifact tags")
  val ioc: A[Boolean] = attribute("ioc", F.booleanFmt, "Artifact is an IOC", false)
  val status: A[ArtifactStatus.Value] = attribute("status", F.enumFmt(ArtifactStatus), "Status of the artifact", ArtifactStatus.Ok)
  val reports: A[String] = attribute("reports", F.textFmt, "Json object that contains all short reports", "{}", O.unaudited)
}

@Singleton
class ArtifactModel @Inject() (
    caseModel: CaseModel,
    val dblists: DBLists,
    artifactSrv: Provider[ArtifactSrv],
    implicit val mat: Materializer,
    implicit val ec: ExecutionContext) extends ChildModelDef[ArtifactModel, Artifact, CaseModel, Case](caseModel, "case_artifact") with ArtifactAttributes with AuditedModel {
  override val removeAttribute: JsObject = Json.obj("status" → ArtifactStatus.Deleted)

  override def apply(attributes: JsObject) = {
    val tags = (attributes \ "tags").asOpt[Seq[JsString]].getOrElse(Nil).distinct
    new Artifact(this, attributes + ("tags" → JsArray(tags)))
  }

  // this method modify request in order to hash artifact and manager file upload
  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = {
    val keys = attrs.keys
    if (keys.contains("data") == keys.contains("attachment"))
      throw BadRequestError(s"Artifact must contain data or attachment (but not both)")
    computeId(parent, attrs).map { id ⇒
      attrs + ("_id" → JsString(id))
    }
  }

  def computeId(parent: Option[BaseEntity], attrs: JsObject): Future[String] = {
    // in order to make sure that there is no duplicated artifact, calculate its id from its content (dataType, data, attachment and parent)
    val mm = new MultiHash("MD5")
    mm.addValue((attrs \ "data").asOpt[JsValue].getOrElse(JsNull))
    mm.addValue((attrs \ "dataType").asOpt[JsValue].getOrElse(JsNull))
    (attrs \ "attachment" \ "filepath").asOpt[String]
      .fold(Future.successful(()))(file ⇒ mm.addFile(file))
      .map { _ ⇒
        mm.addValue(JsString(parent.fold("")(_.id)))
        mm.digest.toString
      }
  }

  override def getStats(entity: BaseEntity): Future[JsObject] = {
    entity match {
      case artifact: Artifact ⇒
        val (_, total) = artifactSrv.get.findSimilar(artifact, Some("0-0"), Nil)
        total.map { t ⇒ Json.obj("seen" → t) }
      case _ ⇒ Future.successful(JsObject(Nil))
    }
  }
}

class Artifact(model: ArtifactModel, attributes: JsObject) extends EntityDef[ArtifactModel, Artifact](model, attributes) with ArtifactAttributes {
  def dblists: DBLists = model.dblists
  override def toJson: JsObject = super.toJson + ("reports" → Json.parse(reports())) // FIXME is parse fails (invalid report)
}
