package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.structure.Graph
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{Entrypoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PublicProperties, Query}
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.scalligraph.{BadRequestError, EntityIdOrName}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputTechnique
import org.thp.thehive.models.{Permissions, RichTechnique, Technique}
import org.thp.thehive.services.TechniqueOps._
import org.thp.thehive.services.TechniqueSrv
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, Results}

import java.io.FileInputStream
import javax.inject.{Inject, Named, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class TechniqueCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    techniqueSrv: TechniqueSrv,
    @Named("with-thehive-schema") implicit val db: Database
) extends QueryableCtrl {
  override val entityName: String                 = "technique"
  override val publicProperties: PublicProperties = properties.technique
  override val initialQuery: Query = Query.init[Traversal.V[Technique]](
    "listTechnique",
    (graph, _) =>
      techniqueSrv
        .startTraversal(graph)
  )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Technique], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    (range, techniqueSteps, _) => techniqueSteps.richPage(range.from, range.to, range.extraData.contains("total"))(_.richTechnique)
  )
  override val outputQuery: Query = Query.output[RichTechnique, Traversal.V[Technique]](_.richTechnique)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Technique]](
    "getTechnique",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, _) => techniqueSrv.get(idOrName)(graph)
  )

  def importMitre: Action[AnyContent] =
    entrypoint("import MITRE ATT&CK techniques")
      .extract("file", FieldsParser.file.on("file"))
      .authPermitted(Permissions.manageTechnique) { implicit request =>
        val file: FFile = request.body("file")

        for {
          inputTechniques <- parseJsonFile(file)
          richTechniques =
            inputTechniques
              .sortBy(_.external_id.length) // sort to create sub-techniques after their parent
              .foldLeft[JsArray](JsArray.empty) { (array, inputTechnique) =>
                val res = db.tryTransaction { implicit graph =>
                  createFromInput(inputTechnique)
                } match {
                  case Failure(e) =>
                    Json.obj("status" -> "Failure", "message" -> e.getMessage)
                  case Success(t) =>
                    Json.obj("status" -> "Success", "mitreId" -> t.techniqueId, "techniqueName" -> t.name)
                }
                array :+ res
              }
        } yield Results.Created(richTechniques)
      }

  def get(techniqueId: String): Action[AnyContent] =
    entrypoint("get technique")
      .authRoTransaction(db) { implicit request => implicit graph =>
        techniqueSrv
          .startTraversal
          .getByTechniqueId(techniqueId)
          .richTechnique
          .getOrFail("Technique")
          .map(richTechnique => Results.Ok(richTechnique.toJson))
      }

  private def parseJsonFile(file: FFile): Try[Seq[InputTechnique]] =
    for {
      stream <- Try(new FileInputStream(file.filepath.toString))
      json = Json.parse(stream)
    } yield (json \ "objects").get.as[Seq[InputTechnique]]

  private def createFromInput(inputTechnique: InputTechnique)(implicit graph: Graph, authContext: AuthContext): Try[Technique with Entity] =
    if (inputTechnique.external_id.isEmpty)
      Failure(BadRequestError(s"A technique with no MITRE id cannot be imported"))
    else if (techniqueSrv.startTraversal.alreadyImported(inputTechnique.external_id))
      Failure(BadRequestError(s"A technique with MITRE id '${inputTechnique.external_id}' already exists in this organisation"))
    else
      for {
        technique <- techniqueSrv.createEntity(inputTechnique.toTechnique)
        _ = if (inputTechnique.x_mitre_is_subtechnique.getOrElse(false)) linkTechnique(technique)
      } yield technique

  private def linkTechnique(child: Technique with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val firstDot = child.techniqueId.indexOf(".")
    val parentId = child.techniqueId.substring(0, firstDot)
    for {
      parent <- techniqueSrv.startTraversal.getByTechniqueId(parentId).getOrFail("Technique")
    } yield techniqueSrv.setParent(child, parent)
  }
}
