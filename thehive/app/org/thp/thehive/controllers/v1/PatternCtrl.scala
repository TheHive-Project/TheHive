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
import org.thp.thehive.dto.v1.InputPattern
import org.thp.thehive.models.{Pattern, Permissions, RichPattern}
import org.thp.thehive.services.PatternOps._
import org.thp.thehive.services.PatternSrv
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, Results}

import java.io.FileInputStream
import javax.inject.{Inject, Named, Singleton}
import scala.util.{Failure, Success, Try}

@Singleton
class PatternCtrl @Inject() (
    entrypoint: Entrypoint,
    properties: Properties,
    patternSrv: PatternSrv,
    @Named("with-thehive-schema") implicit val db: Database
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
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Pattern], IteratorOutput](
    "page",
    FieldsParser[OutputParam],
    {
      case (OutputParam(from, to, extraData), patternSteps, _) =>
        patternSteps.richPage(from, to, extraData.contains("total"))(_.richPatternWithCustomRenderer(patternRenderer(extraData - "total")))
    }
  )
  override val outputQuery: Query = Query.output[RichPattern, Traversal.V[Pattern]](_.richPattern)
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Pattern]](
    "getPattern",
    FieldsParser[EntityIdOrName],
    (idOrName, graph, _) => patternSrv.get(idOrName)(graph)
  )

  def importMitre: Action[AnyContent] =
    entrypoint("import MITRE ATT&CK patterns")
      .extract("file", FieldsParser.file.on("file"))
      .authPermitted(Permissions.managePattern) { implicit request =>
        val file: FFile = request.body("file")

        for {
          inputPatterns <- parseJsonFile(file)
          richPatterns =
            inputPatterns
              .sortBy(_.external_id.length) // sort to create sub-patterns after their parent
              .foldLeft[JsArray](JsArray.empty) { (array, inputPattern) =>
                val res = db.tryTransaction { implicit graph =>
                  createFromInput(inputPattern)
                } match {
                  case Failure(e) =>
                    Json.obj("status" -> "Failure", "message" -> e.getMessage)
                  case Success(t) =>
                    Json.obj("status" -> "Success", "mitreId" -> t.patternId, "patternName" -> t.name)
                }
                array :+ res
              }
        } yield Results.Created(richPatterns)
      }

  def get(patternId: String): Action[AnyContent] =
    entrypoint("get pattern")
      .authRoTransaction(db) { implicit request => implicit graph =>
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
          patternIds <- patternSrv.getCasePatterns(caseId)
        } yield Results.Ok(patternIds.toJson)
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
    else if (patternSrv.startTraversal.alreadyImported(inputPattern.external_id))
      Failure(BadRequestError(s"A pattern with MITRE id '${inputPattern.external_id}' already exists in this organisation"))
    else
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
