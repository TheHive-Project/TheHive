package org.thp.thehive.migration.dto

import org.thp.thehive.models.Audit

case class InputAudit(metaData: MetaData, audit: Audit)
