package org.thp.thehive.migration.dto

import org.thp.thehive.models.Dashboard

case class InputDashboard(metaData: MetaData, organisation: Option[(String, Boolean)], dashboard: Dashboard)
