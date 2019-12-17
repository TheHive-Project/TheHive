package org.thp.thehive.migration.dto

import org.thp.thehive.connector.cortex.models.Action

case class InputAction(metaData: MetaData, objectType: String, action: Action)
