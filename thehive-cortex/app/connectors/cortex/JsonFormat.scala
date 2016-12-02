//package connectors.cortex
//
//import org.elastic4play.models.JsonFormat.enumFormat
//
//import play.api.libs.json._
//
//object JsonFormat {
//  implicit val jobStatusFormat = enumFormat(JobStatus)
//  implicit val jobFormat = Json.format[Job]
//  implicit val analyzerFormat = Json.format[Analyzer]
//}