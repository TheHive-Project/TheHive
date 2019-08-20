package org.thp.thehive.connector.misp.servives

import org.thp.thehive.services.{Connector => TheHiveConnector}

class Connector extends TheHiveConnector {
  override val name: String = "misp"
}
