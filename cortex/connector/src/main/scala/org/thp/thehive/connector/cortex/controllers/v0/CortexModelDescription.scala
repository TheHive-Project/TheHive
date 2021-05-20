package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.services.{EntityDescription, PropertyDescription}
import play.api.Logger

class CortexModelDescription(publicAction: PublicAction, publicAnalyzerTemplate: PublicAnalyzerTemplate, publicJob: PublicJob)
    extends ModelDescription {

  override val logger: Logger = Logger(getClass)
  val metadata = Seq(
    PropertyDescription("createdBy", "user"),
    PropertyDescription("createdAt", "date"),
    PropertyDescription("updatedBy", "user"),
    PropertyDescription("updatedAt", "date")
  )

  override def entityDescriptions: Seq[EntityDescription] =
    Seq(
      EntityDescription(
        "action",
        "/connector/cortex/action",
        "listAction",
        publicAction.publicProperties.list.flatMap(propToDesc("action", _)) ++ metadata
      ),
      EntityDescription(
        "case_artifact_job",
        "/connector/cortex/job",
        "listJob",
        publicJob.publicProperties.list.flatMap(propToDesc("case_artifact_job", _)) ++ metadata
      ),
      EntityDescription(
        "analyzer_template",
        "/connector/cortex/analyzer/template/",
        "listAnalyzerTemplate",
        publicAnalyzerTemplate.publicProperties.list.flatMap(propToDesc("analyzer_template", _)) ++ metadata
      )
    )

  override def customDescription(model: String, propertyName: String): Option[Seq[PropertyDescription]] = None
}
