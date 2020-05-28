package models

import java.util.Date

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

import play.api.Logger
import play.api.libs.json.JsLookupResult.jsLookupResultToJsLookup
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import akka.stream.scaladsl.Sink
import akka.stream.{IOResult, Materializer}
import akka.{Done, NotUsed}
import models.JsonFormat.artifactStatusFormat
import services.{ArtifactSrv, AuditedModel}

import org.elastic4play.models.{AttributeDef, BaseEntity, ChildModelDef, EntityDef, HiveEnumeration, AttributeFormat ⇒ F, AttributeOption ⇒ O}
import org.elastic4play.services.{Attachment, AttachmentSrv, DBLists}
import org.elastic4play.utils.MultiHash
import org.elastic4play.{BadRequestError, InternalError}

object ArtifactStatus extends Enumeration with HiveEnumeration {
  type Type = Value
  val Ok, Deleted = Value
}

trait ArtifactAttributes { _: AttributeDef ⇒
  def dblists: DBLists
  val artifactId: A[String]             = attribute("_id", F.stringFmt, "Artifact id", O.model)
  val data: A[Option[String]]           = optionalAttribute("data", F.stringFmt, "Content of the artifact", O.readonly)
  val dataType: A[String]               = attribute("dataType", F.listEnumFmt("artifactDataType")(dblists), "Type of the artifact", O.readonly)
  val message: A[Option[String]]        = optionalAttribute("message", F.textFmt, "Description of the artifact in the context of the case")
  val startDate: A[Date]                = attribute("startDate", F.dateFmt, "Creation date", new Date)
  val attachment: A[Option[Attachment]] = optionalAttribute("attachment", F.attachmentFmt, "Artifact file content")
  val tlp: A[Long]                      = attribute("tlp", TlpAttributeFormat, "TLP level", 2L)
  val tags: A[Seq[String]]              = multiAttribute("tags", F.stringFmt, "Artifact tags")
  val ioc: A[Boolean]                   = attribute("ioc", F.booleanFmt, "Artifact is an IOC", false)
  val sighted: A[Boolean]               = attribute("sighted", F.booleanFmt, "Artifact has been sighted on the local network", false)
  val status: A[ArtifactStatus.Value]   = attribute("status", F.enumFmt(ArtifactStatus), "Status of the artifact", ArtifactStatus.Ok)
  val reports: A[String]                = attribute("reports", F.textFmt, "Json object that contains all short reports", "{}", O.unaudited)
}

@Singleton
class ArtifactModel @Inject()(
    caseModel: CaseModel,
    val dblists: DBLists,
    attachmentSrv: AttachmentSrv,
    artifactSrv: Provider[ArtifactSrv],
    implicit val mat: Materializer,
    implicit val ec: ExecutionContext
) extends ChildModelDef[ArtifactModel, Artifact, CaseModel, Case](caseModel, "case_artifact", "Observable", "/case/artifact")
    with ArtifactAttributes
    with AuditedModel {
  private[ArtifactModel] lazy val logger = Logger(getClass)
  override val removeAttribute: JsObject = Json.obj("status" → ArtifactStatus.Deleted)

  override def apply(attributes: JsObject): Artifact = {
    val tags = (attributes \ "tags").asOpt[Seq[JsString]].getOrElse(Nil).distinct
    new Artifact(this, attributes + ("tags" → JsArray(tags)))
  }

  // this method modify request in order to hash artifact and manager file upload
  override def creationHook(parent: Option[BaseEntity], attrs: JsObject): Future[JsObject] = {
    val keys = attrs.keys
    if (!keys.contains("message") && (attrs \ "tags").asOpt[Seq[JsValue]].forall(_.isEmpty))
      throw BadRequestError(s"Artifact must contain a message or on ore more tags")
    if (keys.contains("data") == keys.contains("attachment"))
      throw BadRequestError(s"Artifact must contain data or attachment (but not both)")
    computeId(parent.getOrElse(throw InternalError(s"artifact $attrs has no parent")), attrs).map { id ⇒
      attrs + ("_id" → JsString(id))
    }
  }

  override def updateHook(entity: BaseEntity, updateAttrs: JsObject): Future[JsObject] =
    entity match {
      case artifact: Artifact ⇒
        val removeMessage = (updateAttrs \ "message").toOption.exists {
          case JsNull         ⇒ true
          case JsArray(Seq()) ⇒ true
          case _              ⇒ false
        }
        val removeTags = (updateAttrs \ "tags").toOption.exists {
          case JsNull         ⇒ true
          case JsArray(Seq()) ⇒ true
          case _              ⇒ false
        }
        if ((removeMessage && removeTags) ||
            (removeMessage && artifact.tags().isEmpty) ||
            (removeTags && artifact.message().isEmpty))
          Future.failed(BadRequestError(s"Artifact must contain a message or on ore more tags"))
        else
          Future.successful(updateAttrs)
    }

  def computeId(parent: BaseEntity, attrs: JsObject): Future[String] = {
    // in order to make sure that there is no duplicated artifact, calculate its id from its content (dataType, data, attachment and parent)
    val mm = new MultiHash("MD5")
    mm.addValue((attrs \ "data").asOpt[JsValue].getOrElse(JsNull))
    mm.addValue((attrs \ "dataType").asOpt[JsValue].getOrElse(JsNull))
    for {
      IOResult(_, _) ← (attrs \ "attachment" \ "filepath")
        .asOpt[String]
        .fold(Future.successful(IOResult(0, Success(Done))))(file ⇒ mm.addFile(file))
      _ ← (attrs \ "attachment" \ "id")
        .asOpt[String]
        .fold(Future.successful(NotUsed: NotUsed)) { fileId ⇒
          mm.addFile(attachmentSrv.source(fileId))
        }
    } yield {
      mm.addValue(JsString(parent.id))
      mm.digest.toString
    }
  }

  override def getStats(entity: BaseEntity): Future[JsObject] =
    entity match {
      case artifact: Artifact ⇒
        val (similarArtifacts, total) = artifactSrv.get.findSimilar(artifact, Some("0-1"), Seq("-ioc"))
        for {
          ioc ← similarArtifacts.runWith(Sink.headOption).map(_.fold(false)(_.ioc()))
          t   ← total
        } yield Json.obj("seen" → t, "ioc" → ioc)
      case _ ⇒ Future.successful(JsObject.empty)
    }
}

class Artifact(model: ArtifactModel, attributes: JsObject) extends EntityDef[ArtifactModel, Artifact](model, attributes) with ArtifactAttributes {
  def dblists: DBLists          = model.dblists
  override def toJson: JsObject = super.toJson + ("reports" → Json.parse(reports())) // FIXME is parse fails (invalid report)
}
