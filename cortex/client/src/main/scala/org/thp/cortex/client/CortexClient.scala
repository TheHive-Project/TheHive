package org.thp.cortex.client

import play.api.libs.ws.WSClient

import org.thp.cortex.dto.v0._

class CortexClient(baseUrl: String)(implicit ws: WSClient) {
  val job = new BaseClient[InputArtifact, OutputJob](s"$baseUrl/api/job")
}
