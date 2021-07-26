package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.models.IndexType
import org.thp.thehive.controllers.ModelDescription
import org.thp.thehive.services.{EntityDescription, PropertyDescription}
import play.api.Logger

class CortexModelDescription(publicAction: PublicAction, publicAnalyzerTemplate: PublicAnalyzerTemplate, publicJob: PublicJob)
    extends ModelDescription {

  override val logger: Logger = Logger(getClass)
  val metadata = Seq(
    PropertyDescription("createdBy", "user", indexType = IndexType.standard),
    PropertyDescription("createdAt", "date", indexType = IndexType.standard),
    PropertyDescription("updatedBy", "user", indexType = IndexType.standard),
    PropertyDescription("updatedAt", "date", indexType = IndexType.standard)
  )

  override def entityDescriptions: Seq[EntityDescription] =
    Seq(
      EntityDescription(
        "action",
        "/connector/cortex/action",
        "listAction",
        publicAction.publicProperties.list.flatMap(propertyDescription("action", _)) ++ metadata
      ),
      EntityDescription(
        "case_artifact_job",
        "/connector/cortex/job",
        "listJob",
        publicJob.publicProperties.list.flatMap(propertyDescription("case_artifact_job", _)) ++ metadata
      ),
      EntityDescription(
        "analyzer_template",
        "/connector/cortex/analyzer/template/",
        "listAnalyzerTemplate",
        publicAnalyzerTemplate.publicProperties.list.flatMap(propertyDescription("analyzer_template", _)) ++ metadata
      )
    )
}
