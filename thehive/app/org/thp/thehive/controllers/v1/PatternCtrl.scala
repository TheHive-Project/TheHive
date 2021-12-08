package org.thp.thehive.controllers.v1

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{Entrypoint, FFile, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.{ParamQuery, PublicProperties, Query}
import org.thp.scalligraph.traversal.TraversalOps.TraversalOpsDefs
import org.thp.scalligraph.traversal.{Graph, IteratorOutput, Traversal}
import org.thp.scalligraph.{BadRequestError, EntityIdOrName}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputPattern
import org.thp.thehive.models.{Pattern, Permissions, RichPattern}
import org.thp.thehive.services.PatternOps._
import org.thp.thehive.services.PatternSrv
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, Results}

import java.io.FileInputStream
import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class PatternCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    patternSrv: PatternSrv,
    db: Database
) extends QueryableCtrl
    with PatternRenderer {
  override val entityName: String                 = "pattern"
  override val publicProperties: PublicProperties = properties.pattern
  override val initialQuery: Query = Query.init[Traversal.V[Pattern]](
    "listPattern",
    (graph, _) =>
      patternSrv
        .startTraversal(graph)
  )
  override def pageQuery(limitedCountThreshold: Long): ParamQuery[OutputParam] =
    Query.withParam[OutputParam, Traversal.V[Pattern], IteratorOutput](
      "page",
      {
        case (OutputParam(from, to, extraData), patternSteps, _) =>
          patternSteps.richPage(from, to, extraData.contains("total"), limitedCountThreshold)(
            _.richPatternWithCustomRenderer(patternRenderer(extraData - "total"))
          )
      }
    )
  override val outputQuery: Query = Query.output[RichPattern, Traversal.V[Pattern]](_.richPattern)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Pattern]](
    "getPattern",
    (idOrName, graph, _) => patternSrv.get(idOrName)(graph)
  )

  def importMitre: Action[AnyContent] =
    entrypoint("import MITRE ATT&CK patterns")
      .extract("file", FieldsParser.file.on("file"))
      .authPermitted(Permissions.managePattern) { implicit request =>
        val file: FFile = request.body("file")

        for {
          inputPatterns <- parseJsonFile(file)
          importedPatterns =
            inputPatterns
              .sortBy(_.external_id.length) // sort to create sub-patterns after their parent
              .foldLeft[JsArray](JsArray.empty) { (array, inputPattern) =>
                array :+ db.tryTransaction { implicit graph =>
                  createFromInput(inputPattern).transform(
                    t => Success(Json.obj("status" -> "Success", "mitreId" -> t.patternId, "patternName" -> t.name)),
                    e => Success(Json.obj("status" -> "Failure", "message" -> e.getMessage))
                  )
                }.get
              }
        } yield Results.Created(importedPatterns)
      }

  def get(patternId: String): Action[AnyContent] =
    entrypoint("get pattern")
      .authRoTransaction(db) { _ => implicit graph =>
        patternSrv
          .get(EntityIdOrName(patternId))
          .richPattern
          .getOrFail("Pattern")
          .map(richPattern => Results.Ok(richPattern.toJson))
      }

  def getCasePatterns(caseId: String): Action[AnyContent] =
    entrypoint("get case patterns")
      .authRoTransaction(db) { implicit request => implicit graph =>
        for {
          patterns <- patternSrv.getCasePatterns(caseId)
        } yield Results.Ok(patterns.toJson)
      }

  def delete(patternId: String): Action[AnyContent] =
    entrypoint("delete pattern")
      .authPermittedTransaction(db, Permissions.managePattern) { implicit request => implicit graph =>
        patternSrv
          .getOrFail(EntityIdOrName(patternId))
          .flatMap(patternSrv.remove)
          .map(_ => Results.NoContent)
      }

  private def parseJsonFile(file: FFile): Try[Seq[InputPattern]] =
    for {
      json <- Try(Json.parse(new FileInputStream(file.filepath.toString)))
    } yield (json \ "objects").as[Seq[InputPattern]]

  private def createFromInput(inputPattern: InputPattern)(implicit graph: Graph, authContext: AuthContext): Try[Pattern with Entity] =
    if (inputPattern.external_id.isEmpty)
      Failure(BadRequestError(s"A pattern with no MITRE id cannot be imported"))
    else if (inputPattern.`type` != "attack-pattern")
      Failure(BadRequestError(s"Only patterns with type attack-pattern are imported, this one is ${inputPattern.`type`}"))
    else if (patternSrv.startTraversal.alreadyImported(inputPattern.external_id))
      // Update a pattern
      for {
        pattern        <- patternSrv.get(EntityIdOrName(inputPattern.external_id)).getOrFail("Pattern")
        updatedPattern <- patternSrv.update(pattern, inputPattern.toPattern)
        _ = if (inputPattern.x_mitre_is_subtechnique) linkPattern(pattern)
      } yield updatedPattern
    else
      // Create a pattern
      for {
        pattern <- patternSrv.createEntity(inputPattern.toPattern)
        _ = if (inputPattern.x_mitre_is_subtechnique) linkPattern(pattern)
      } yield pattern

  private def linkPattern(child: Pattern with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    val firstDot = child.patternId.indexOf(".")
    if (firstDot == -1)
      Failure(BadRequestError(s"Invalid sub-pattern patternId ${child.patternId} (must contain a dot"))
    else {
      val parentId = child.patternId.substring(0, firstDot)
      for {
        parent <- patternSrv.startTraversal.getByPatternId(parentId).getOrFail("Pattern")
        _      <- patternSrv.setParent(child, parent)
      } yield ()
    }
  }
}
