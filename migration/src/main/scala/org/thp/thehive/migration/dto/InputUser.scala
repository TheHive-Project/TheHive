package org.thp.thehive.migration.dto

import org.thp.thehive.models.User

case class InputUser(metaData: MetaData, user: User, organisations: Map[String, String], avatar: Option[InputAttachment])
