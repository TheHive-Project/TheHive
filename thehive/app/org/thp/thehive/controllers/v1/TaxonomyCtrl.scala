package org.thp.thehive.controllers.v1

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{Entrypoint, FFile, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query._
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{Graph, IteratorOutput, Traversal}
import org.thp.scalligraph.{BadRequestError, EntityIdOrName, RichSeq}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputTaxonomy
import org.thp.thehive.models.{Permissions, RichTaxonomy, Tag, Taxonomy}
import org.thp.thehive.services.TaxonomyOps._
import org.thp.thehive.services.{TagSrv, TaxonomySrv}
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class TaxonomyCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    taxonomySrv: TaxonomySrv,
    tagSrv: TagSrv,
    db: Database
) extends QueryableCtrl
    with TaxonomyRenderer {

  override val entityName: String                 = "taxonomy"
  override val publicProperties: PublicProperties = properties.taxonomy
  override val initialQuery: Query =
    Query.init[Traversal.V[Taxonomy]]("listTaxonomy", (graph, authContext) => taxonomySrv.startTraversal(graph).visible(authContext))
  override val getQuery: ParamQuery[EntityIdOrName] =
    Query.initWithParam[EntityIdOrName, Traversal.V[Taxonomy]](
      "getTaxonomy",
      FieldsParser[EntityIdOrName],
      (idOrName, graph, authContext) => taxonomySrv.get(idOrName)(graph).visible(authContext)
    )
  override val pageQuery: ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Taxonomy], IteratorOutput](
      "page",
      {
        case (OutputParam(from, to, extraData), taxoSteps, authContext) =>
          taxoSteps.richPage(from, to, extraData.contains("total")) {
            _.richTaxonomyWithCustomRenderer(taxoStatsRenderer(extraData - "total"))
          }
      }
    )
  override val outputQuery: Query =
    Query.outputWithContext[RichTaxonomy, Traversal.V[Taxonomy]]((traversal, _) => traversal.richTaxonomy)
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Taxonomy], Traversal.V[Tag]]("tags", (traversal, _) => traversal.tags)
  )

  def create: Action[AnyContent] =
    entrypoint("import taxonomy")
      .extract("taxonomy", FieldsParser[InputTaxonomy])
      .authPermittedTransaction(db, Permissions.manageTaxonomy) { implicit request => implicit graph =>
        for {
          richTaxonomy <- createFromInput(request.body("taxonomy"))
        } yield Results.Created(richTaxonomy.toJson)
      }

  def importZip: Action[AnyContent] =
    entrypoint("import taxonomies zip")
      .extract("file", FieldsParser.file.on("file"))
      .authPermitted(Permissions.manageTaxonomy) { implicit request =>
        val file: FFile = request.body("file")
        val zipFile     = new ZipFile(file.filepath.toString)
        val headers = zipFile
          .getFileHeaders
          .iterator()
          .asScala

        for {
          inputTaxos <-
            headers
              .filter(h => h.getFileName.endsWith("machinetag.json"))
              .toTry(parseJsonFile(zipFile, _))
          richTaxos = inputTaxos.foldLeft[JsArray](JsArray.empty) { (array, taxo) =>
            val res = db.tryTransaction { implicit graph =>
              createFromInput(taxo)
            } match {
              case Failure(e) =>
                Json.obj("namespace" -> taxo.namespace, "status" -> "Failure", "message" -> e.getMessage)
              case Success(t) =>
                Json.obj("namespace" -> t.namespace, "status" -> "Success", "tagsImported" -> t.tags.size)
            }
            array :+ res
          }
        } yield Results.Created(richTaxos)
      }

  private def parseJsonFile(zipFile: ZipFile, h: FileHeader): Try[InputTaxonomy] =
    Try(Json.parse(zipFile.getInputStream(h)).as[InputTaxonomy]).recoverWith {
      case _ => Failure(BadRequestError(s"File '${h.getFileName}' does not comply with the MISP taxonomy formatting"))
    }

  private def createFromInput(inputTaxo: InputTaxonomy)(implicit graph: Graph, authContext: AuthContext): Try[RichTaxonomy] = {
    // Create tags
    val tagValues = inputTaxo.values.getOrElse(Seq())
    val tags = tagValues.flatMap { value =>
      value.entry.map(e => Tag(inputTaxo.namespace, value.predicate, Some(e.value), e.expanded, e.colour.getOrElse(tagSrv.freeTagColour)))
    }

    // Create a tag for predicates with no tags associated
    val predicateWithNoTags = inputTaxo.predicates.map(_.value).diff(tagValues.map(_.predicate))
    val allTags             = tags ++ predicateWithNoTags.map(p => Tag(inputTaxo.namespace, p, None, None, tagSrv.freeTagColour))

    if (inputTaxo.namespace.isEmpty)
      Failure(BadRequestError(s"A taxonomy with no namespace cannot be imported"))
    else if (inputTaxo.namespace.startsWith("_freetags"))
      Failure(BadRequestError(s"Namespace _freetags is restricted for TheHive"))
    else if (taxonomySrv.startTraversal.alreadyImported(inputTaxo.namespace))
      Failure(BadRequestError(s"A taxonomy with namespace '${inputTaxo.namespace}' already exists in this organisation"))
    else
      for {
        tagsEntities <- allTags.toTry(t => tagSrv.create(t))
        richTaxonomy <- taxonomySrv.create(inputTaxo.toTaxonomy, tagsEntities)
      } yield richTaxonomy
  }

  def get(taxonomyId: String): Action[AnyContent] =
    entrypoint("get taxonomy")
      .authRoTransaction(db) { implicit request => implicit graph =>
        taxonomySrv
          .get(EntityIdOrName(taxonomyId))
          .visible
          .richTaxonomy
          .getOrFail("Taxonomy")
          .map(taxonomy => Results.Ok(taxonomy.toJson))
      }

  def toggleActivation(taxonomyId: String, isActive: Boolean): Action[AnyContent] =
    entrypoint("toggle taxonomy")
      .authPermittedTransaction(db, Permissions.manageTaxonomy) { implicit request => implicit graph =>
        val toggleF = if (isActive) taxonomySrv.activate _ else taxonomySrv.deactivate _
        toggleF(EntityIdOrName(taxonomyId)).map(_ => Results.NoContent)
      }

  def delete(taxoId: String): Action[AnyContent] =
    entrypoint("delete taxonomy")
      .authPermittedTransaction(db, Permissions.manageTaxonomy) { implicit request => implicit graph =>
        for {
          taxo <-
            taxonomySrv
              .get(EntityIdOrName(taxoId))
              .visible
              .getOrFail("Taxonomy")
          tags <- Try(taxonomySrv.get(taxo).tags.toSeq)
          _    <- tags.toTry(t => tagSrv.delete(t))
          _    <- taxonomySrv.delete(taxo)
        } yield Results.NoContent
      }

}
