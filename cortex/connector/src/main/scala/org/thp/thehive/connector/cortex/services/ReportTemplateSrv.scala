package org.thp.thehive.connector.cortex.services

import java.util.zip.ZipFile

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.EntitySteps
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{BaseVertexSteps, Database, Entity}
import org.thp.scalligraph.services._
import org.thp.thehive.connector.cortex.models.ReportType.ReportType
import org.thp.thehive.connector.cortex.models.{ReportTemplate, ReportType}

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.Try

@Singleton
class ReportTemplateSrv @Inject()(
    implicit db: Database
) extends VertexSrv[ReportTemplate, ReportTemplateSteps] {

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ReportTemplateSteps = new ReportTemplateSteps(raw)

  /**
    * Creates or updates if found templates contained in a zip file
    *
    * @param file the zip file
    * @param graph needed graph connection for db query
    * @param authContext needed auth context for db query
    * @return
    */
  def importZipFile(file: ZipFile)(implicit graph: Graph, authContext: AuthContext): Iterator[Try[ReportTemplate with Entity]] =
    for {
      entry <- file.entries.asScala
      if !entry.isDirectory && entry.getName.endsWith(".html")
    } yield for {
      stream  <- Try(file.getInputStream(entry))
      content <- Try(Source.fromInputStream(stream).mkString)
      _ = stream.close()
    } yield {
      val Array(analyzerId, ext, _*) = (entry.getName + "/").split("/", 3)
      val reportType                 = Try(ReportType.withName(if (ext.endsWith(".html")) ext.dropRight(5) else ext)).getOrElse(ReportType.long)
      val update = for {
        template <- initSteps
          .forWorkerAndType(
            analyzerId,
            reportType
          )
          .getOrFail()
        updatedTemplate <- initSteps
          .get(template._id)
          .update("content" -> content)
      } yield updatedTemplate

      update.getOrElse(create(ReportTemplate(analyzerId, content, reportType)))
    }
}

@EntitySteps[ReportTemplate]
class ReportTemplateSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph)
    extends BaseVertexSteps[ReportTemplate, ReportTemplateSteps](raw) {

  /**
    * Looks for a template that has the workerId and reportType
    * supplied
    *
    * @param workerId the id to look for
    * @param rType the report type to look for
    * @return
    */
  def forWorkerAndType(workerId: String, rType: ReportType): ReportTemplateSteps = newInstance(
    raw.and(
      _.has(Key("workerId") of workerId),
      _.has(Key("reportType") of rType)
    )
  )

  override def newInstance(raw: GremlinScala[Vertex]): ReportTemplateSteps = new ReportTemplateSteps(raw)

  /**
    * Removes entities from database
    */
  def remove(): Unit = {
    raw.drop().iterate()
    ()
  }
}
