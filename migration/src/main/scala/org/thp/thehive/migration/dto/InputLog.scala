package org.thp.thehive.migration.dto

import org.thp.thehive.models.Log

case class InputLog(metaData: MetaData, log: Log, attachments: Seq[InputAttachment])
