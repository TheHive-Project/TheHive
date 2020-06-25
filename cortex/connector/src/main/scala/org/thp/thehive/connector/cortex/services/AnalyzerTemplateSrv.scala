package org.thp.thehive.connector.cortex.services

import java.util.zip.{ZipEntry, ZipFile}

import com.google.inject.name.Named
import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.VertexSteps
import org.thp.scalligraph.{CreateError, EntitySteps}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.models.AnalyzerTemplate
import org.thp.thehive.controllers.v0.Conversion._
import play.api.libs.json.{JsObject, Json}

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Try}
@Singleton
class AnalyzerTemplateSrv @Inject() (
    implicit @Named("with-thehive-cortex-schema") db: Database,
    auditSrv: CortexAuditSrv
) extends VertexSrv[AnalyzerTemplate, AnalyzerTemplateSteps] {

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): AnalyzerTemplateSteps = new AnalyzerTemplateSteps(raw)

  override def get(idOrName: String)(implicit graph: Graph): AnalyzerTemplateSteps =
    if (db.isValidId(idOrName)) getByIds(idOrName)
    else initSteps.getByAnalyzerId(idOrName)

  def readZipEntry(file: ZipFile, entry: ZipEntry): Try[String] =
    Try {
      val stream = file.getInputStream(entry)
      try Source.fromInputStream(stream).mkString
      finally stream.close()
    }

  def create(analyzerTemplate: AnalyzerTemplate)(implicit graph: Graph, authContext: AuthContext): Try[AnalyzerTemplate with Entity] =
    if (initSteps.getByAnalyzerId(analyzerTemplate.workerId).exists())
      Failure(CreateError(s"Analyzer template for ${analyzerTemplate.workerId} already exists"))
    else
      for {
        created <- createEntity(analyzerTemplate)
        _       <- auditSrv.analyzerTemplate.create(created, created.toJson)
      } yield created

  override def update(
      steps: AnalyzerTemplateSteps,
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(AnalyzerTemplateSteps, JsObject)] =
    auditSrv.mergeAudits(super.update(steps, propertyUpdaters)) {
      case (analyzerTemplateSteps, updatedFields) =>
        analyzerTemplateSteps
          .newInstance()
          .getOrFail()
          .flatMap(auditSrv.analyzerTemplate.update(_, updatedFields))
    }

  def remove(analyzerTemplate: AnalyzerTemplate with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] = {
    get(analyzerTemplate).remove()
    auditSrv.analyzerTemplate.delete(analyzerTemplate)
  }

  /**
    * Creates or updates if found templates contained in a zip file
    *
    * @param db          needed database connection
    * @param file        the zip file
    * @param authContext needed auth context for db query
    * @return
    */
  def importZipFile(db: Database, file: ZipFile)(implicit authContext: AuthContext): Map[String, Try[AnalyzerTemplate with Entity]] =
    file
      .entries
      .asScala
      .filterNot(_.isDirectory)
      .toSeq
      .groupBy(_.getName.takeWhile(c => c != '/' && c != '.'))
      .flatMap {
        case (name, entries) if entries.lengthCompare(1) == 0 => List(name                                                               -> entries.head)
        case (name, entries)                                  => entries.filterNot(_.getName.endsWith("short.html")).headOption.map(name -> _)
      }
      .foldLeft(Map.empty[String, Try[AnalyzerTemplate with Entity]]) {
        case (templateMap, (analyzerId, _)) if templateMap.contains(analyzerId) => templateMap
        case (templateMap, (analyzerId, entry)) =>
          val analyzerTemplate = readZipEntry(file, entry)
            .flatMap { content =>
              db.tryTransaction { implicit graph =>
                (for {
                  updated <- get(analyzerId).updateOne("content" -> content)
                  _       <- auditSrv.analyzerTemplate.update(updated, Json.obj("content" -> content))
                } yield updated).recoverWith {
                  case _ =>
                    for {
                      created <- create(AnalyzerTemplate(analyzerId, content))
                      _       <- auditSrv.analyzerTemplate.create(created, created.toJson)
                    } yield created
                }
              }
            }
          templateMap + (analyzerId -> analyzerTemplate)
      }
}

@EntitySteps[AnalyzerTemplate]
class AnalyzerTemplateSteps(raw: GremlinScala[Vertex])(implicit @Named("with-thehive-cortex-schema") db: Database, graph: Graph)
    extends VertexSteps[AnalyzerTemplate](raw) {

  def get(idOrAnalyzerId: String): AnalyzerTemplateSteps =
    if (db.isValidId(idOrAnalyzerId)) this.getByIds(idOrAnalyzerId)
    else getByAnalyzerId(idOrAnalyzerId)

  /**
    * Looks for a template that has the workerId supplied
    *
    * @param workerId the id to look for
    * @return
    */
  def getByAnalyzerId(workerId: String): AnalyzerTemplateSteps = new AnalyzerTemplateSteps(raw.has(Key("workerId") of workerId))

  override def newInstance(newRaw: GremlinScala[Vertex]): AnalyzerTemplateSteps = new AnalyzerTemplateSteps(newRaw)
  override def newInstance(): AnalyzerTemplateSteps                             = new AnalyzerTemplateSteps(raw.clone())
}
