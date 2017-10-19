package connectors.misp

import play.api.Logger

import services.CustomWSAPI

case class MispConnection(
    name: String,
    baseUrl: String,
    key: String,
    ws: CustomWSAPI,
    caseTemplate: Option[String],
    artifactTags: Seq[String]) {

  private[MispConnection] lazy val logger = Logger(getClass)

  logger.info(
    s"""Add MISP connection $name
       |\turl:           $baseUrl
       |\tproxy:         ${ws.proxy}
       |\tcase template: ${caseTemplate.getOrElse("<not set>")}
       |\tartifact tags: ${artifactTags.mkString}""".stripMargin)

  private[misp] def apply(url: String) =
    ws.url(s"$baseUrl/$url")
      .withHttpHeaders(
        "Authorization" → key,
        "Accept" → "application/json")
}