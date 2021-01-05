package org.thp.thehive.connector.cortex.services

import java.util.zip.{ZipEntry, ZipFile}
import com.google.inject.name.Named

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.query.PropertyUpdater
import org.thp.scalligraph.services._
import org.thp.scalligraph.traversal.{Graph, Traversal}
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{CreateError, EntityIdOrName, EntityName}
import org.thp.thehive.connector.cortex.controllers.v0.Conversion._
import org.thp.thehive.connector.cortex.models.AnalyzerTemplate
import org.thp.thehive.connector.cortex.services.AnalyzerTemplateOps._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.services.OrganisationSrv
import play.api.libs.json.{JsObject, Json}

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Failure, Try}

@Singleton
class AnalyzerTemplateSrv @Inject() (implicit
    @Named("with-thehive-cortex-schema") db: Database,
    auditSrv: CortexAuditSrv,
    organisationSrv: OrganisationSrv
) extends VertexSrv[AnalyzerTemplate] {

  override def getByName(name: String)(implicit graph: Graph): Traversal.V[AnalyzerTemplate] =
    startTraversal.getByAnalyzerId(name)

  def readZipEntry(file: ZipFile, entry: ZipEntry): Try[String] =
    Try {
      val stream = file.getInputStream(entry)
      try Source.fromInputStream(stream).mkString
      finally stream.close()
    }

  def create(analyzerTemplate: AnalyzerTemplate)(implicit graph: Graph, authContext: AuthContext): Try[AnalyzerTemplate with Entity] =
    if (startTraversal.getByAnalyzerId(analyzerTemplate.workerId).exists)
      Failure(CreateError(s"Analyzer template for ${analyzerTemplate.workerId} already exists"))
    else
      for {
        created <- createEntity(analyzerTemplate)
        _       <- auditSrv.analyzerTemplate.create(created, created.toJson)
      } yield created

  override def update(
      traversal: Traversal.V[AnalyzerTemplate],
      propertyUpdaters: Seq[PropertyUpdater]
  )(implicit graph: Graph, authContext: AuthContext): Try[(Traversal.V[AnalyzerTemplate], JsObject)] =
    auditSrv.mergeAudits(super.update(traversal, propertyUpdaters)) {
      case (analyzerTemplateSteps, updatedFields) =>
        analyzerTemplateSteps
          .clone()
          .getOrFail("AnalyzerTemplate")
          .flatMap(auditSrv.analyzerTemplate.update(_, updatedFields))
    }

  def remove(analyzerTemplate: AnalyzerTemplate with Entity)(implicit graph: Graph, authContext: AuthContext): Try[Unit] =
    organisationSrv.getOrFail(authContext.organisation).flatMap { organisation =>
      get(analyzerTemplate).remove()
      auditSrv.analyzerTemplate.delete(analyzerTemplate, organisation)
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
        case (name, entries) if entries.lengthCompare(1) == 0 => List(name -> entries.head)
        case (name, entries)                                  => entries.filterNot(_.getName.endsWith("short.html")).headOption.map(name -> _)
      }
      .foldLeft(Map.empty[String, Try[AnalyzerTemplate with Entity]]) {
        case (templateMap, (analyzerId, _)) if templateMap.contains(analyzerId) => templateMap
        case (templateMap, (analyzerId, entry)) =>
          val analyzerTemplate = readZipEntry(file, entry)
            .flatMap { content =>
              db.tryTransaction { implicit graph =>
                (for {
                  updated <- get(EntityName(analyzerId)).update(_.content, content).getOrFail("AnalyzerTemplate")
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

object AnalyzerTemplateOps {
  implicit class AnalyzerTemplateOpsDefs(traversal: Traversal.V[AnalyzerTemplate]) {

    def get(idOrAnalyzerId: EntityIdOrName): Traversal.V[AnalyzerTemplate] =
      idOrAnalyzerId.fold(traversal.getByIds(_), getByAnalyzerId)

    /**
      * Looks for a template that has the workerId supplied
      *
      * @param workerId the id to look for
      * @return
      */
    def getByAnalyzerId(workerId: String): Traversal.V[AnalyzerTemplate] = traversal.has(_.workerId, workerId)
  }
}
