package models

import java.util.Date

import javax.inject.{Inject, Provider, Singleton}
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

import play.api.libs.json.JsObject
import play.api.{Configuration, Logger}

import services.AuditedModel

import org.elastic4play.models.{
  Attribute,
  AttributeDef,
  AttributeFormat,
  BaseEntity,
  EntityDef,
  EnumerationAttributeFormat,
  ListEnumerationAttributeFormat,
  ModelDef,
  MultiAttributeFormat,
  ObjectAttributeFormat,
  OptionalAttributeFormat,
  StringAttributeFormat,
  AttributeOption => O
}
import org.elastic4play.services.{AuditableAction, AuxSrv}
import org.elastic4play.services.JsonFormat.auditableActionFormat

trait AuditAttributes { _: AttributeDef =>
  def detailsAttributes: Seq[Attribute[_]]

  val operation: A[AuditableAction.Value] = attribute("operation", AttributeFormat.enumFmt(AuditableAction), "Operation", O.readonly)
  val details: A[JsObject]                = attribute("details", AttributeFormat.objectFmt(detailsAttributes), "Details", JsObject.empty, O.readonly)
  val otherDetails: A[Option[String]]     = optionalAttribute("otherDetails", AttributeFormat.textFmt, "Other details", O.readonly)
  val objectType: A[String]               = attribute("objectType", AttributeFormat.stringFmt, "Table affected by the operation", O.readonly)
  val objectId: A[String]                 = attribute("objectId", AttributeFormat.stringFmt, "Object targeted by the operation", O.readonly)
  val base: A[Boolean]                    = attribute("base", AttributeFormat.booleanFmt, "Indicates if this operation is the first done for a http query", O.readonly)
  val startDate: A[Date]                  = attribute("startDate", AttributeFormat.dateFmt, "Date and time of the operation", new Date, O.readonly)
  val rootId: A[String]                   = attribute("rootId", AttributeFormat.stringFmt, "Root element id (routing id)", O.readonly)
  val requestId: A[String]                = attribute("requestId", AttributeFormat.stringFmt, "Id of the request that do the operation", O.readonly)
}

@Singleton
class AuditModel(auditName: String, auditedModels: immutable.Set[AuditedModel], auxSrvProvider: Provider[AuxSrv], implicit val ec: ExecutionContext)
    extends ModelDef[AuditModel, Audit](auditName, "Audit", "/audit")
    with AuditAttributes {

  lazy val auxSrv: AuxSrv = auxSrvProvider.get()

  @Inject() def this(
      configuration: Configuration,
      auditedModels: immutable.Set[AuditedModel],
      auxSrvProvider: Provider[AuxSrv],
      ec: ExecutionContext
  ) =
    this(configuration.get[String]("audit.name"), auditedModels, auxSrvProvider, ec)

  private[AuditModel] lazy val logger = Logger(getClass)

  def mergeAttributeFormat(context: String, format1: AttributeFormat[_], format2: AttributeFormat[_]): Option[AttributeFormat[_]] =
    (format1, format2) match {
      case (OptionalAttributeFormat(f1), f2)                                              => mergeAttributeFormat(context, f1, f2)
      case (f1, OptionalAttributeFormat(f2))                                              => mergeAttributeFormat(context, f1, f2)
      case (MultiAttributeFormat(f1), MultiAttributeFormat(f2))                           => mergeAttributeFormat(context, f1, f2).map(MultiAttributeFormat(_))
      case (f1, EnumerationAttributeFormat(_) | ListEnumerationAttributeFormat(_))        => mergeAttributeFormat(context, f1, StringAttributeFormat)
      case (EnumerationAttributeFormat(_) | ListEnumerationAttributeFormat(_), f2)        => mergeAttributeFormat(context, StringAttributeFormat, f2)
      case (ObjectAttributeFormat(subAttributes1), ObjectAttributeFormat(subAttributes2)) => mergeAttributes(context, subAttributes1 ++ subAttributes2)
      case (f1, f2) if f1 == f2                                                           => Some(f1)
      case (f1, f2) =>
        logger.warn(s"Attribute $f1 != $f2")
        None

    }

  def mergeAttributes(context: String, attributes: Seq[Attribute[_]]): Option[ObjectAttributeFormat] = {
    val mergeAttributes: Iterable[Option[Attribute[_]]] = attributes
      .groupBy(_.attributeName)
      .map {
        case (_name, _attributes) =>
          _attributes
            .map(a => Some(a.format))
            .reduce[Option[AttributeFormat[_]]] {
              case (Some(f1), Some(f2)) => mergeAttributeFormat(context + "." + _name, f1, f2)
              case _                    => None
            }
            .map {
              case oaf: OptionalAttributeFormat[_] => oaf: AttributeFormat[_]
              case maf: MultiAttributeFormat[_]    => maf: AttributeFormat[_]
              case f                               => OptionalAttributeFormat(f): AttributeFormat[_]
            }
            .map(format => Attribute("audit", _name, format, Nil, None, ""))
            .orElse {
              logger.error(
                s"Mapping is not consistent on attribute $context:\n${_attributes.map(a => a.modelName + "/" + a.attributeName + ": " + a.format.name).mkString("\n")}"
              )
              None
            }
      }

    if (mergeAttributes.exists(_.isEmpty))
      None
    else
      Some(ObjectAttributeFormat(mergeAttributes.flatten.toSeq))
  }

  def detailsAttributes: Seq[Attribute[_]] =
    mergeAttributes(
      "audit",
      auditedModels
        .flatMap(_.attributes)
        .filter(a => a.isModel && !a.isUnaudited)
        .toSeq
    ).map(_.subAttributes)
      .getOrElse(Nil)

  override def getStats(entity: BaseEntity): Future[JsObject] =
    entity match {
      case audit: Audit =>
        auxSrv(audit.objectType(), audit.objectId(), 10, withStats = false, removeUnaudited = true)
          .recover {
            case t =>
              logger.error("Audit stats failure", t)
              JsObject.empty
          }
      case other =>
        logger.warn(s"Request caseStats from a non-case entity ?! ${other.getClass}:$other")
        Future.successful(JsObject.empty)
    }

  override def apply(attributes: JsObject): Audit = new Audit(this, attributes)
}

class Audit(model: AuditModel, attributes: JsObject) extends EntityDef[AuditModel, Audit](model, attributes) with AuditAttributes {
  def detailsAttributes: Seq[Attribute[_]] = Nil
}
