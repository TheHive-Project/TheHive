package services

import java.io.File
import java.nio.file.{ Files, Path, Paths }

import javax.inject.{ Inject, Singleton }

import scala.annotation.implicitNotFound
import scala.collection.JavaConversions.{ asScalaSet, iterableAsScalaIterable }
import scala.concurrent.{ ExecutionContext, Future }

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import play.api.{ Configuration, Logger }
import play.api.cache.CacheApi
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.Json

import org.reflections.Reflections

import org.elastic4play.NotFoundError
import org.elastic4play.controllers.Fields
import org.elastic4play.services.{ CreateSrv, FindSrv, GetSrv }
import org.elastic4play.services.{ QueryDef, TempSrv }
import org.elastic4play.services.JsonFormat.configWrites

import models.{ Analyzer, AnalyzerDesc, AnalyzerModel, ExternalAnalyzer, JavaAnalyzer }
import models.JsonFormat.analyzerWrites

@Singleton
class AnalyzerSrv(analyzerPath: Path,
                  analyzerPackages: Seq[String],
                  analyzerConfig: JsObject,
                  analyzerDefaultReportPath: Path,
                  analyzerModel: AnalyzerModel,
                  cache: CacheApi,
                  getSrv: GetSrv,
                  createSrv: CreateSrv,
                  findSrv: FindSrv,
                  userSrv: UserSrv,
                  tempSrv: TempSrv,
                  implicit val ec: ExecutionContext,
                  implicit val mat: Materializer) {

  @Inject def this(configuration: Configuration,
                   analyzerModel: AnalyzerModel,
                   cache: CacheApi,
                   getSrv: GetSrv,
                   createSrv: CreateSrv,
                   findSrv: FindSrv,
                   userSrv: UserSrv,
                   tempSrv: TempSrv,
                   ec: ExecutionContext,
                   mat: Materializer) =
    this(Paths.get(configuration.getString("analyzer.path").get),
      configuration.getStringSeq("analyzer.packages").get,
      configWrites.writes(configuration.getConfig("analyzer.config").get),
      Paths.get(configuration.getString("analyzer.defaultReport").get),
      analyzerModel,
      cache,
      getSrv,
      createSrv,
      findSrv,
      userSrv,
      tempSrv,
      ec,
      mat)

  val log = Logger(getClass)

  def get(id: String): Future[Analyzer] =
    localAnalyzers
      .find(_.id == id)
      .fold[Future[Analyzer]](Future.failed(NotFoundError(s"analyzer $id not found")))(a => Future.successful(a))

  def find(queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[AnalyzerDesc, NotUsed], Future[Long]) = {
    findSrv[AnalyzerModel, AnalyzerDesc](analyzerModel, queryDef, range, sortBy)
  }

  def getDesc(id: String) = getSrv[AnalyzerModel, AnalyzerDesc](analyzerModel, id)

  private def readInfo(file: Path) = {
    val source = scala.io.Source.fromFile(file.toFile())
    try {
      Json.parse(source.mkString)
    } finally { source.close() }
  }

  private def externalAnalyzers = for {
    infoFile <- Files.newDirectoryStream(analyzerPath, "*.json").toSeq
    if Files.isReadable(infoFile)
    info = readInfo(infoFile)
    name <- (info \ "name").asOpt[String] orElse { log.warn(s"name is missing in $infoFile"); None }
    version <- (info \ "version").asOpt[String] orElse { log.warn(s"version is missing in $infoFile"); None }
    description <- (info \ "description").asOpt[String] orElse { log.warn(s"description is missing in $infoFile"); None }
    dataTypeList <- (info \ "dataTypeList").asOpt[Seq[String]] orElse { log.warn(s"dataTypeList is missing in $infoFile"); None }
    command <- (info \ "command").asOpt[String] orElse { log.warn(s"command is missing in $infoFile"); None }
    config = (info \ "config").asOpt[JsObject].getOrElse(JsObject(Nil))
    baseConfig = (info \ "baseConfig").asOpt[String].flatMap(c => (analyzerConfig \ c).asOpt[JsObject]).getOrElse(JsObject(Nil))
    reportPath <- (info \ "report").asOpt[String].map(r => analyzerPath.resolve(r))
    absoluteCommand = analyzerPath.resolve(Paths.get(command.replaceAll("[\\/]", File.separator)))
    _ = log.info(s"Register analyzer $name $version (${(name + "_" + version).replaceAll("\\.", "_")})")
  } yield ExternalAnalyzer(name, version, description, dataTypeList, absoluteCommand, baseConfig.deepMerge(config), reportPath, tempSrv)

  private lazy val javaAnalyzers = {
    new Reflections(new org.reflections.util.ConfigurationBuilder()
      .forPackages(analyzerPackages: _*)
      .setScanners(new org.reflections.scanners.SubTypesScanner(false)))
      .getSubTypesOf(classOf[JavaAnalyzer])
      .filterNot(c => java.lang.reflect.Modifier.isAbstract(c.getModifiers))
      .map(c => c.newInstance)
      .toSeq
  }

  lazy val localAnalyzers = {
    val analyzers = (externalAnalyzers ++ javaAnalyzers)
    userSrv.inInitAuthContext { implicit authContext =>
      analyzers.foreach { analyzer =>
        val fields = Fields(Json.toJson(analyzer).as[JsObject])
          .set("_id", analyzer.id)
          .unset("id")
        createSrv[AnalyzerModel, AnalyzerDesc](analyzerModel, fields)
          .onFailure { case t => log.error(s"Analyzer registration of ${analyzer.id} failed", t) }
      }
      Future.successful(analyzers)
    }
    analyzers

  }

  def availableFor(dataType: String): Future[Seq[Analyzer]] = Future.successful {
    localAnalyzers.filter { _.dataTypeList.contains(dataType) }
  }

  def getReport(analyzerId: String, flavor: String) = {
    get(analyzerId)
      .flatMap { analyzer =>
        analyzer.getReport(flavor)
      }
      .fallbackTo(Future(scala.io.Source.fromFile(analyzerDefaultReportPath.resolve(flavor + ".html").toFile).mkString))
      .recover { case _ => "" }
  }
}