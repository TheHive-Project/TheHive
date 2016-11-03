package models

import java.io.{ BufferedReader, InputStreamReader }
import java.nio.file.{ Files, Path }

import scala.concurrent.{ ExecutionContext, Future, blocking }
import scala.sys.process.{ BasicIO, Process, ProcessIO }

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO

import play.api.Logger
import play.api.libs.json.{ JsObject, JsString, Json }

import org.elastic4play.models.{ AttributeDef, AttributeFormat => F, AttributeOption => O, EntityDef, ModelDef }
import org.elastic4play.services.{ AttachmentSrv, AuthContext, TempSrv }

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException

/**
 * New analyzers should :
 *  - create a file (or stdout ?) containing the main report in JSON format
 *  - create optional additional files
 *  - the main report can contain artifacts. They are materialized by a JsObject that have an attribute "_type":"artifact" and all possible artifact attributes
 *  - the main report should reference additional files as artifact. Filename must be in "filename" attribute.
 *  All output files must be in a unique working directory, dedicated to the current analyze
 */
abstract class AnalyzerInfo {
  val name: String
  val version: String
  val description: String
  val dataTypeList: Seq[String]
  val id = (name + "_" + version).replaceAll("\\.", "_")
  def getReport(flavor: String): Future[String]
}

sealed abstract class Analyzer extends AnalyzerInfo {
  def analyze(attachmentSrv: AttachmentSrv, artifact: Artifact)(implicit authContext: AuthContext): Future[(JobStatus.Type, JsObject)]
}

case class ExternalAnalyzer(
    name: String,
    version: String,
    description: String,
    dataTypeList: Seq[String],
    command: Path,
    config: JsObject,
    reportPath: Path,
    tempSrv: TempSrv)(implicit val ec: ExecutionContext, val mat: Materializer) extends Analyzer {
  val log = Logger(getClass)
  private val osexec = if (System.getProperty("os.name").toLowerCase.contains("win"))
    (c: String) => s"""cmd /c $c"""
  else
    (c: String) => s"""sh -c "./$c" """

  private[ExternalAnalyzer] def analyzeHelper(attachmentSrv: AttachmentSrv, artifact: Artifact)(process: JsObject => (JobStatus.Type, JsObject)): Future[(JobStatus.Type, JsObject)] = {
    artifact.attachment() match {
      case Some(attachment) =>
        val artifactFile = Files.createTempFile("TheHive_", s"_$id.tmp").toAbsolutePath()
        attachmentSrv.source(attachment.id).runWith(FileIO.toPath(artifactFile))
          .map {
            case result if result.wasSuccessful => artifact.attributes + ("config" -> config) + ("file" -> JsString(artifactFile.toString))
            case result                         => throw result.getError
          }
          .map(process)
          .andThen { case _ => Files.delete(artifactFile) }
      case None => Future { process(artifact.attributes + ("config" -> config)) }
    }
  }

  def analyze(attachmentSrv: AttachmentSrv, artifact: Artifact)(implicit authContext: AuthContext): Future[(JobStatus.Type, JsObject)] = {
    val output = new StringBuffer
    val error = new StringBuffer

    analyzeHelper(attachmentSrv, artifact) { input =>
      blocking {
        log.info(s"Execute ${osexec(command.getFileName.toString)} in ${command.getParent.toFile.getAbsoluteFile.getName}")
        val exitValue = Process(osexec(command.getFileName.toString), command.getParent.toFile).run(
          new ProcessIO(
            { stdin =>
              try stdin.write(input.toString.getBytes("UTF-8"))
              finally stdin.close()
            },
            { stdout =>
              val reader = new BufferedReader(new InputStreamReader(stdout, "UTF-8"))
              try BasicIO.processLinesFully { line =>
                output.append(line).append(System.lineSeparator())
                ()
              }(reader.readLine)
              finally reader.close()
            },
            { stderr =>
              val reader = new BufferedReader(new InputStreamReader(stderr, "UTF-8"))
              try BasicIO.processLinesFully { line =>
                error.append(line).append(System.lineSeparator())
                ()
              }(reader.readLine)
              finally reader.close()
            })).exitValue
        val result = Json.parse(output.toString).as[JsObject]
        if (exitValue == 0)
          (JobStatus.Success, result)
        else
          (JobStatus.Failure, result)
      }
    }
      .recover {
        case _: JsonMappingException =>
          error.append(output)
          (JobStatus.Failure, JsObject(Seq("errorMessage" -> JsString(s"Error: Invalid output\n$error"))))
        case _: JsonParseException =>
          error.append(output)
          (JobStatus.Failure, JsObject(Seq("errorMessage" -> JsString(s"Error: Invalid output\n$error"))))
        case t: Throwable =>
          (JobStatus.Failure, JsObject(Seq("errorMessage" -> JsString(t.getMessage + ":" + t.getStackTrace().mkString("", "\n\t", "\n")))))
      }
  }

  def getReport(flavor: String): Future[String] =
    Future { scala.io.Source.fromFile(reportPath.resolve(s"$flavor.html").toFile()).mkString }
}

abstract class JavaAnalyzer extends Analyzer

trait AnalyzerAttributes { _: AttributeDef =>
  val analyzerId = attribute("_id", F.stringFmt, "Analyzer ID", O.readonly)
  val analyzerName = attribute("name", F.stringFmt, "Name of the analyzer", O.readonly)
  val version = attribute("version", F.stringFmt, "Version", O.readonly)
  val description = attribute("description", F.textFmt, "Description", O.readonly)
  val dataTypeList = multiAttribute("dataTypeList", F.stringFmt, "List of accepted data types")
}
class AnalyzerModel extends ModelDef[AnalyzerModel, AnalyzerDesc]("analyzer") with AnalyzerAttributes
class AnalyzerDesc(model: AnalyzerModel, attributes: JsObject) extends EntityDef[AnalyzerModel, AnalyzerDesc](model, attributes) with AnalyzerAttributes { analyzer =>
  def info = new AnalyzerInfo {
    val name = analyzer.analyzerName()
    val version = analyzer.version()
    val description = analyzer.description()
    val dataTypeList = analyzer.dataTypeList()
    def getReport(flavor: String): Future[String] = Future.successful("")
  }
}