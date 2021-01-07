package org.thp.thehive.dto.v1

import play.api.libs.json.{Format, Json, Reads, Writes}

import java.util.Date

case class InputTechnique(
    external_id: String,
    name: String,
    description: Option[String],
    kill_chain_phases: Seq[InputKillChainPhase],
    url: String,
    `type`: String,
    x_mitre_platforms: Seq[String],
    x_mitre_data_sources: Seq[String],
    x_mitre_version: Option[String]
)

case class InputReference(
    source_name: String,
    external_id: String,
    url: String
)

case class InputKillChainPhase(
    kill_chain_name: String,
    phase_name: String
)

object InputReference {
  implicit val reads: Reads[InputReference] = Reads[InputReference] { json =>
    for {
      source_name <- (json \ "source_name").validate[String]
      external_id <- (json \ "external_id").validateOpt[String]
      url         <- (json \ "url").validateOpt[String]
    } yield InputReference(
      source_name,
      external_id.getOrElse(""),
      url.getOrElse("")
    )
  }

  implicit val writes: Writes[InputReference] = Json.writes[InputReference]
}

object InputKillChainPhase {
  implicit val reads: Reads[InputKillChainPhase] = Reads[InputKillChainPhase] { json =>
    for {
      kill_chain_name <- (json \ "kill_chain_name").validate[String]
      phase_name      <- (json \ "phase_name").validate[String]
    } yield InputKillChainPhase(
      kill_chain_name,
      phase_name
    )
  }

  implicit val writes: Writes[InputKillChainPhase] = Json.writes[InputKillChainPhase]
}

object InputTechnique {
  implicit val reads: Reads[InputTechnique] = Reads[InputTechnique] { json =>
    for {
      references <- (json \ "external_references").validate[Seq[InputReference]]
      mitreReference = references.find(_.source_name == "mitre-attack")
      name                 <- (json \ "name").validate[String]
      description          <- (json \ "description").validateOpt[String]
      kill_chain_phases    <- (json \ "kill_chain_phases").validateOpt[Seq[InputKillChainPhase]]
      techniqueType        <- (json \ "type").validate[String]
      x_mitre_platforms    <- (json \ "x_mitre_platforms").validateOpt[Seq[String]]
      x_mitre_data_sources <- (json \ "x_mitre_data_sources").validateOpt[Seq[String]]
      x_mitre_version      <- (json \ "x_mitre_version").validateOpt[String]
    } yield InputTechnique(
      mitreReference.map(_.external_id).getOrElse(""),
      name,
      description,
      kill_chain_phases.getOrElse(Seq()),
      mitreReference.map(_.url).getOrElse(""),
      techniqueType,
      x_mitre_platforms.getOrElse(Seq()),
      x_mitre_data_sources.getOrElse(Seq()),
      x_mitre_version
    )
  }

  implicit val writes: Writes[InputTechnique] = Json.writes[InputTechnique]
}

case class OutputTechnique(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String] = None,
    _createdAt: Date,
    _updatedAt: Option[Date] = None,
    name: String,
    description: Option[String],
    parent: Option[String]
)

object OutputTechnique {
  implicit val format: Format[OutputTechnique] = Json.format[OutputTechnique]
}
