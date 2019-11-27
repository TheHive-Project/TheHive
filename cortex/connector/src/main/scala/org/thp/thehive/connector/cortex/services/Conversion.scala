package org.thp.thehive.connector.cortex.services

import io.scalaland.chimney.dsl._
import org.thp.cortex.dto.v0.{OutputMinireport, OutputArtifact, JobStatus => CortexJobStatus}
import org.thp.thehive.connector.cortex.models.JobStatus
import org.thp.thehive.models.{Observable, ReportTag, ReportTagLevel}

object Conversion {

  implicit class CortexJobStatusOps(jobStatus: CortexJobStatus.Value) {

    def toJobStatus: JobStatus.Value =
      jobStatus match {
        case CortexJobStatus.Failure    => JobStatus.Failure
        case CortexJobStatus.InProgress => JobStatus.InProgress
        case CortexJobStatus.Success    => JobStatus.Success
        case CortexJobStatus.Waiting    => JobStatus.Waiting
        case CortexJobStatus.Deleted    => JobStatus.Deleted
      }
  }

  implicit class CortexOutputArtifactOps(artifact: OutputArtifact) {

    def toObservable: Observable =
      artifact
        .into[Observable]
        .withFieldComputed(_.message, _.message)
        .withFieldComputed(_.tlp, _.tlp)
        .withFieldConst(_.ioc, false)
        .withFieldConst(_.sighted, false)
        .transform
  }

  implicit class CortexAnalyzerTagOps(outputAnalyzerTag: OutputMinireport) {

    def toAnalyzerTag(analyzerName: String): ReportTag =
      outputAnalyzerTag
        .into[ReportTag]
        .withFieldConst(_.origin, analyzerName)
        .withFieldComputed(_.level, t => ReportTagLevel.withName(t.level))
        .transform
  }
}
