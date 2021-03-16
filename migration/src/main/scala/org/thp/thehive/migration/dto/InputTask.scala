package org.thp.thehive.migration.dto

import org.thp.thehive.models.Task

case class InputTask(metaData: MetaData, task: Task, owner: Option[String], organisations: Set[String])
