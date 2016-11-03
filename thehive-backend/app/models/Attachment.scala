//package models
//
//import org.scalactic.One
//import play.api.libs.json.JsSuccess
//import org.elastic4play.models.HashAttributeFormat
//import org.elastic4play.models.StringAttributeFormat
//import scala.annotation.meta.field
//import org.elastic4play.controllers.InputValue
//import org.scalactic.Good
//import jdk.nashorn.internal.codegen.types.LongType
//import org.scalactic.Bad
//import org.elastic4play.models.NumberAttributeFormat
//import play.api.libs.json.JsObject
//import org.elastic4play.controllers.FileInputValue
//import play.api.libs.json.JsValue
//import org.elastic4play.controllers.JsonInputValue
//import org.elastic4play.models.AttributeFormat
//import services.Attachment
//import org.elastic4play.InvalidFormatAttributeError
//import scala.annotation.meta.field
//import jdk.nashorn.internal.codegen.types.LongType
//
//object AttachmentAttributeFormat extends AttributeFormat[Attachment]("attachment") {
//  override def checkJson(subNames: Seq[String], value: JsValue) = fileInputValueFormat.reads(value) match {
//    case JsSuccess(_, _) if subNames.isEmpty => Good(value)
//    case _                                   => Bad(One(InvalidFormatAttributeError("", name, JsonInputValue(value))))
//  }
//  val forbiddenChar = Seq('/', '\n', '\r', '\t', '\u0000', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':', ';')
//  override def inputValueToJson(subNames: Seq[String], value: InputValue): JsValue Or Every[AttributeError] = {
//    if (!subNames.isEmpty)
//      Bad(One(InvalidFormatAttributeError("", name, value)))
//    else
//      value match {
//        case fiv: FileInputValue if fiv.name.intersect(forbiddenChar).isEmpty => Good(Json.toJson(fiv)(fileInputValueFormat))
//        case _ => Bad(One(InvalidFormatAttributeError("", name, value)))
//      }
//  }
//  override def fromInputValue(subNames: Seq[String], value: InputValue): Attachment Or Every[AttributeError] = sys.error("not implemented")
//  override def elasticToJson(values: Seq[Any]): Option[JsValue] = values match {
//    case Seq(m: Map[_, _]) =>
//      for {
//        name <- Option(m.get("name")).flatMap(n => StringAttributeFormat.elasticToJson(Seq(n)))
//        hashes <- Option(m.get("hashes")).flatMap(h => HashAttributeFormat.elasticToJson(Seq(h)))
//        size <- Option(m.get("size")).flatMap(s => NumberAttributeFormat.elasticToJson(Seq(s)))
//        contentType <- Option(m.get("contentType")).flatMap(ct => StringAttributeFormat.elasticToJson(Seq(ct)))
//        id <- Option(m.get("id")).flatMap(i => StringAttributeFormat.elasticToJson(Seq(i)))
//      } yield JsObject(Seq(
//        "name" -> name,
//        "hashes" -> hashes,
//        "size" -> size,
//        "contentType" -> contentType,
//        "id" -> id))
//    case _ => None
//  }
//  override val swaggerType = Json.obj("type" -> "File", "required" -> true) // swagger bug : File input must be required
//  override def elasticType(attributeName: String) = field(attributeName, NestedType) as (
//    field("name", StringType) index "not_analyzed",
//    field("hashes", StringType) index "not_analyzed",
//    field("size", LongType),
//    field("contentType", StringType),
//    field("id", StringType))
//})