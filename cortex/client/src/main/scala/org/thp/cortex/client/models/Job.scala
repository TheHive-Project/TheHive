package org.thp.cortex.client.models

import java.util.Date

class Job(
    id: String,
    workerId: String,
    workerName: String,
    workerDefinition: String,
    artifact: CortexArtifact,
    date: Date,
    status: JobStatus.Type
)
