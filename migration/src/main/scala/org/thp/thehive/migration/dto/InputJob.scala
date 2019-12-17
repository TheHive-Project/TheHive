package org.thp.thehive.migration.dto

import org.thp.thehive.connector.cortex.models.Job

case class InputJob(metaData: MetaData, job: Job)
