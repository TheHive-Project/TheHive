package org.thp.thehive.dto.v1

import play.api.libs.json.{Format, Json, Reads, Writes}

import java.util.Date

case class InputPattern(
    external_id: String,
    name: String,
    description: Option[String],
    kill_chain_phases: Seq[InputKillChainPhase],
    url: String,
    `type`: String,
    revoked: Boolean,
    x_mitre_data_sources: Seq[String],
    x_mitre_defense_bypassed: Seq[String],
    x_mitre_detection: Option[String],
    x_mitre_is_subtechnique: Boolean,
    // TODO x_mitre_tactic_type ?
    // TODO x_mitre_permissions_required ?
    x_mitre_platforms: Seq[String],
    x_mitre_remote_support: Boolean,
    x_mitre_system_requirements: Seq[String],
    x_mitre_version: Option[String]
)

case class InputReference(
    source_name: String,
    external_id: Option[String],
    url: Option[String]
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
      external_id,
      url
    )
  }

  implicit val writes: Writes[InputReference] = Json.writes[InputReference]
}

object InputKillChainPhase {
  implicit val reads: Reads[InputKillChainPhase] = Json.reads[InputKillChainPhase]

  implicit val writes: Writes[InputKillChainPhase] = Json.writes[InputKillChainPhase]
}

object InputPattern {
  implicit val reads: Reads[InputPattern] = Reads[InputPattern] { json =>
    for {
      references <- (json \ "external_references").validate[Seq[InputReference]]
      mitreReference = references.find(ref => isSourceNameValid(ref.source_name))
      name                        <- (json \ "name").validate[String]
      description                 <- (json \ "description").validateOpt[String]
      kill_chain_phases           <- (json \ "kill_chain_phases").validateOpt[Seq[InputKillChainPhase]]
      techniqueType               <- (json \ "type").validate[String]
      revoked                     <- (json \ "revoked").validateOpt[Boolean]
      x_mitre_data_sources        <- (json \ "x_mitre_data_sources").validateOpt[Seq[String]]
      x_mitre_defense_bypassed    <- (json \ "x_mitre_defense_bypassed").validateOpt[Seq[String]]
      x_mitre_detection           <- (json \ "x_mitre_detection").validateOpt[String]
      x_mitre_is_subtechnique     <- (json \ "x_mitre_is_subtechnique").validateOpt[Boolean]
      x_mitre_platforms           <- (json \ "x_mitre_platforms").validateOpt[Seq[String]]
      x_mitre_remote_support      <- (json \ "x_mitre_remote_support").validateOpt[Boolean]
      x_mitre_system_requirements <- (json \ "x_mitre_system_requirements").validateOpt[Seq[String]]
      x_mitre_version             <- (json \ "x_mitre_version").validateOpt[String]
    } yield InputPattern(
      mitreReference.flatMap(_.external_id).getOrElse(""),
      name,
      description,
      kill_chain_phases.getOrElse(Seq()),
      mitreReference.flatMap(_.url).getOrElse(""),
      techniqueType,
      revoked.getOrElse(false),
      x_mitre_data_sources.getOrElse(Seq()),
      x_mitre_defense_bypassed.getOrElse(Seq()),
      x_mitre_detection,
      x_mitre_is_subtechnique.getOrElse(false),
      x_mitre_platforms.getOrElse(Seq()),
      x_mitre_remote_support.getOrElse(false),
      x_mitre_system_requirements.getOrElse(Seq()),
      x_mitre_version
    )
  }

  private def isSourceNameValid(reference: String): Boolean =
    reference == "mitre-attack"

  implicit val writes: Writes[InputPattern] = Json.writes[InputPattern]
}

case class OutputPattern(
    _id: String,
    _type: String,
    _createdBy: String,
    _updatedBy: Option[String],
    _createdAt: Date,
    _updatedAt: Option[Date],
    patternId: String,
    name: String,
    description: Option[String],
    tactics: Set[String],
    url: String,
    patternType: String,
    revoked: Boolean,
    dataSources: Seq[String],
    defenseBypassed: Seq[String],
    detection: Option[String],
    platforms: Seq[String],
    remoteSupport: Boolean,
    systemRequirements: Seq[String],
    version: Option[String],
    parent: Option[String]
)

object OutputPattern {
  implicit val format: Format[OutputPattern] = Json.format[OutputPattern]
}
