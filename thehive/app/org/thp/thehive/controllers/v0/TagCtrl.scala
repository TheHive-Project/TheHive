package org.thp.thehive.controllers.v0

import java.nio.file.Files

import javax.inject.{Inject, Named, Singleton}
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.controllers.{Entrypoint, FFile, FieldsParser, Renderer}
import org.thp.scalligraph.models.{Database, Entity, UMapping}
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, IteratorOutput, Traversal}
import org.thp.scalligraph.{EntityIdOrName, RichSeq}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.models.{Permissions, Tag}
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services.TagSrv
import play.api.libs.json.{JsNumber, JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import scala.util.Try

class TagCtrl @Inject() (
    override val entrypoint: Entrypoint,
    override val db: Database,
    tagSrv: TagSrv,
    @Named("v0") override val queryExecutor: QueryExecutor,
    override val publicData: PublicTag
) extends QueryCtrl {
  def importTaxonomy: Action[AnyContent] =
    entrypoint("import taxonomy")
      .extract("file", FieldsParser.file.optional.on("file"))
      .extract("content", FieldsParser.jsObject.optional.on("content"))
      .authPermittedTransaction(db, Permissions.manageTag) { implicit request => implicit graph =>
        val file: Option[FFile]       = request.body("file")
        val content: Option[JsObject] = request.body("content")
        val tags = file
          .fold(Seq.empty[Tag])(ffile => parseTaxonomy(Json.parse(Files.newInputStream(ffile.filepath)))) ++
          content.fold(Seq.empty[Tag])(parseTaxonomy)

        tags
          .filterNot(tagSrv.startTraversal.getTag(_).exists)
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
      value <-
        values
          .foldLeft((Seq.empty[JsObject], Seq.empty[String]))((acc, v) => distinct((v \ "predicate").asOpt[String], acc, v))
          ._1
      predicate <- (value \ "predicate").asOpt[String].toList
      entry <-
        (value \ "entry")
          .asOpt[Seq[JsObject]]
          .getOrElse(Nil)
          .foldLeft((Seq.empty[JsObject], Seq.empty[String]))((acc, v) => distinct((v \ "value").asOpt[String], acc, v))
          ._1
      v <- (entry \ "value").asOpt[String]
      colour =
        (entry \ "colour")
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
      predicate <-
        predicates
          .foldLeft((Seq.empty[JsObject], Seq.empty[String]))((acc, v) => distinct((v \ "value").asOpt[String], acc, v))
          ._1
      v <- (predicate \ "value").asOpt[String]
      e = (predicate \ "expanded").asOpt[String]
      colour =
        (predicate \ "colour")
          .asOpt[String]
          .map(parseColour)
          .getOrElse(0) // black
    } yield Tag(namespace, v, None, e, colour)

  def get(tagId: String): Action[AnyContent] =
    entrypoint("get tag")
      .authRoTransaction(db) { _ => implicit graph =>
        tagSrv
          .getOrFail(EntityIdOrName(tagId))
          .map { tag =>
            Results.Ok(tag.toJson)
          }
      }
}

@Singleton
class PublicTag @Inject() (tagSrv: TagSrv) extends PublicData {
  override val entityName: String  = "tag"
  override val initialQuery: Query = Query.init[Traversal.V[Tag]]("listTag", (graph, _) => tagSrv.startTraversal(graph))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Tag], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, tagSteps, _) => tagSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[Tag with Entity]
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Tag]](
    "getTag",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, _) => tagSrv.get(idOrName)(graph)
  )
  implicit val stringRenderer: Renderer[String] = Renderer.toJson[String, String](identity)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Tag], Traversal.V[Tag]]("fromCase", (tagSteps, _) => tagSteps.fromCase),
    Query[Traversal.V[Tag], Traversal.V[Tag]]("fromObservable", (tagSteps, _) => tagSteps.fromObservable),
    Query[Traversal.V[Tag], Traversal.V[Tag]]("fromAlert", (tagSteps, _) => tagSteps.fromAlert),
    Query[Traversal.V[Tag], Traversal[String, Vertex, Converter[String, Vertex]]]("text", (tagSteps, _) => tagSteps.displayName),
    Query.output[String, Traversal[String, Vertex, Converter[String, Vertex]]]
  )
  override val publicProperties: PublicProperties = PublicPropertyListBuilder[Tag]
    .property("namespace", UMapping.string)(_.field.readonly)
    .property("predicate", UMapping.string)(_.field.readonly)
    .property("value", UMapping.string.optional)(_.field.readonly)
    .property("description", UMapping.string.optional)(_.field.readonly)
    .property("text", UMapping.string)(
      _.select(_.displayName) // FIXME add filter
//        .filter((_, tags) =>
//          tags
//            .graphMap[String, String, Converter.Identity[String]](
//              { v =>
//                val namespace = UMapping.string.getProperty(v, "namespace")
//                val predicate = UMapping.string.getProperty(v, "predicate")
//                val value     = UMapping.string.optional.getProperty(v, "value")
//                Tag(namespace, predicate, value, None, 0).toString
//              },
//              Converter.identity[String]
//            )
//        )
//        .converter(_ => Converter.identity[String])
        .readonly
    )
    .build
}
