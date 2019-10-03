package org.thp.thehive.controllers.v0

import java.nio.file.Files

import scala.util.Failure

import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.Inject
import org.thp.scalligraph.controllers.{EntryPoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PublicProperty, Query}
import org.thp.scalligraph.steps.PagedResult
import org.thp.scalligraph.{AuthorizationError, RichSeq}
import org.thp.thehive.dto.v0.OutputTag
import org.thp.thehive.models.{Permissions, Tag}
import org.thp.thehive.services.{TagSrv, TagSteps}

class TagCtrl @Inject()(
    entryPoint: EntryPoint,
    db: Database,
    tagSrv: TagSrv
) extends QueryableCtrl {
  import TagConversion._

  override val entityName: String                           = "tag"
  override val publicProperties: List[PublicProperty[_, _]] = tagProperties
  override val initialQuery: Query                          = Query.init[TagSteps]("listTag", (graph, _) => tagSrv.initSteps(graph))
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, TagSteps, PagedResult[Tag with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, tagSteps, _) => tagSteps.page(range.from, range.to, withTotal = true)
  )
  override val outputQuery: Query = Query.output[Tag with Entity, OutputTag]
  override val getQuery: ParamQuery[IdOrName] = Query.initWithParam[IdOrName, TagSteps](
    "getTag",
    FieldsParser[IdOrName],
    (param, graph, _) => tagSrv.get(param.idOrName)(graph)
  )

  def importTaxonomy: Action[AnyContent] =
    entryPoint("import taxonomy")
      .extract("file", FieldsParser.file.optional)
      .extract("content", FieldsParser.jsObject.optional)
      .authTransaction(db) { implicit request => implicit graph =>
        if (request.permissions.contains(Permissions.manageTag)) {
          val file: Option[FFile]       = request.body("file")
          val content: Option[JsObject] = request.body("content")
          val tags = file.fold(Seq.empty[Tag])(ffile => parseTaxonomy(Json.parse(Files.newInputStream(ffile.filepath)))) ++
            content.fold(Seq.empty[Tag])(parseTaxonomy)
          tags.toTry(tagSrv.create).map(ts => Results.Ok(JsNumber(ts.size)))
        } else Failure(AuthorizationError("You don't have permission to manage tags"))
      }
}
