package services

import play.api.libs.json.{ Json, OWrites }

object JsonFormat {
  implicit val caseSimilarityWrites: OWrites[CaseSimilarity] = OWrites[CaseSimilarity] {
    case CaseSimilarity(caze, similarIocCount, iocCount, similarArtifactCount, artifactCount) ⇒
      Json.obj(
        "id" → caze.id,
        "_id" → caze.id,
        "caseId" → caze.caseId(),
        "title" → caze.title(),
        "tags" → caze.tags(),
        "status" → caze.status(),
        "severity" → caze.severity(),
        "resolutionStatus" → caze.resolutionStatus(),
        "tlp" → caze.tlp(),
        "startDate" → caze.startDate(),
        "endDate" → caze.endDate(),
        "similarIocCount" → similarIocCount,
        "iocCount" → iocCount,
        "similarArtifactCount" → similarArtifactCount,
        "artifactCount" → artifactCount)
  }
}
