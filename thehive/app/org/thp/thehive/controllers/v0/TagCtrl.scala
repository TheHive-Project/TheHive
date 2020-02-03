package org.thp.thehive.controllers.v0

import java.nio.file.Files

import javax.inject.Inject
import org.thp.scalligraph.RichSeq
import org.thp.scalligraph.controllers.{EntryPoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models.{Permissions, Tag}
import org.thp.thehive.services.{TagSrv, TagSteps}
import play.api.libs.json.{JsNumber, JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Try

class TagCtrl @Inject() (
    entryPoint: EntryPoint,
    db: Database,
    properties: Properties,
    tagSrv: TagSrv
) extends QueryableCtrl {
  override val entityName: String                           = "tag"
  override val publicProperties: List[PublicProperty[_, _]] = properties.tag ::: metaProperties[TagSteps]
  override val initialQuery: Query                          = Query.init[TagSteps]("listTag", (graph, _) => tagSrv.initSteps(graph))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, TagSteps, PagedResult[Tag with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, tagSteps, _) => tagSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[Tag with Entity]()
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, TagSteps](
    "getTag",
    FieldsParser[IdOrName],
    (param, graph, _) => tagSrv.get(param.idOrName)(graph)
  )

  def importTaxonomy: Action[AnyContent] =
    entryPoint("import taxonomy")
      .extract("file", FieldsParser.file.optional.on("file"))
      .extract("content", FieldsParser.jsObject.optional.on("content"))
      .authPermittedTransaction(db, Permissions.manageTag) { implicit request => implicit graph =>
        val file: Option[FFile]       = request.body("file")
        val content: Option[JsObject] = request.body("content")
        val tags = file
          .fold(Seq.empty[Tag])(ffile => parseTaxonomy(Json.parse(Files.newInputStream(ffile.filepath)))) ++
          content.fold(Seq.empty[Tag])(parseTaxonomy)

        tags
          .filterNot(tagSrv.initSteps.get(_).exists())
          .toTry(tagSrv.create)
          .map(ts => Results.Ok(JsNumber(ts.size)))
      }

  def parseTaxonomy(taxonomy: JsValue): Seq[Tag] =
    (taxonomy \ "namespace").asOpt[String].fold(Seq.empty[Tag]) { namespace =>
      (taxonomy \ "values").asOpt[Seq[JsObject]].filter(_.nonEmpty) match {
        case Some(values) => parseValues(namespace, values)
        case _            => (taxonomy \ "predicates").asOpt[Seq[JsObject]].fold(Seq.empty[Tag])(parsePredicates(namespace, _))
      }
    }

  def parseValues(namespace: String, values: Seq[JsObject]): Seq[Tag] =
    for {
      value <- values
        .foldLeft((Seq.empty[JsObject], Seq.empty[String]))((acc, v) => distinct((v \ "predicate").asOpt[String], acc, v))
        ._1
      predicate <- (value \ "predicate").asOpt[String].toList
      entry <- (value \ "entry")
        .asOpt[Seq[JsObject]]
        .getOrElse(Nil)
        .foldLeft((Seq.empty[JsObject], Seq.empty[String]))((acc, v) => distinct((v \ "value").asOpt[String], acc, v))
        ._1
      v <- (entry \ "value").asOpt[String]
      colour = (entry \ "colour")
        .asOpt[String]
        .map(parseColour)
        .getOrElse(0) // black
      e = (entry \ "description").asOpt[String] orElse (entry \ "expanded").asOpt[String]
    } yield Tag(namespace, predicate, Some(v), e, colour)

  def parseColour(colour: String): Int = if (colour(0) == '#') Try(Integer.parseUnsignedInt(colour.tail, 16)).getOrElse(0) else 0

  private def distinct(valueOpt: Option[String], acc: (Seq[JsObject], Seq[String]), v: JsObject): (Seq[JsObject], Seq[String]) =
    if (valueOpt.isDefined && acc._2.contains(valueOpt.get)) acc
    else (acc._1 :+ v, valueOpt.fold(acc._2)(acc._2 :+ _))

  def parsePredicates(namespace: String, predicates: Seq[JsObject]): Seq[Tag] =
    for {
      predicate <- predicates
        .foldLeft((Seq.empty[JsObject], Seq.empty[String]))((acc, v) => distinct((v \ "value").asOpt[String], acc, v))
        ._1
      v <- (predicate \ "value").asOpt[String]
      e = (predicate \ "expanded").asOpt[String]
      colour = (predicate \ "colour")
        .asOpt[String]
        .map(parseColour)
        .getOrElse(0) // black
    } yield Tag(namespace, v, None, e, colour)

  def get(tagId: String): Action[AnyContent] =
    entryPoint("get tag")
      .authRoTransaction(db) { _ => implicit graph =>
        tagSrv
          .getOrFail(tagId)
          .map { tag =>
            Results.Ok(tag.toJson)
          }
      }
}
