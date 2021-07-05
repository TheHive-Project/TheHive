package org.thp.thehive

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.settings.ParserSettings
import play.core.server.{AkkaHttpServer, ServerProvider}

/** A custom Akka HTTP server with advanced configuration. */
class CustomAkkaHttpServer(context: AkkaHttpServer.Context) extends AkkaHttpServer(context) {
  override protected def createParserSettings(): ParserSettings =
    super.createParserSettings().withCustomMethods(HttpMethod.custom("PROPFIND"))
  }

/** A factory that instantiates a CustomAkkaHttpServer. */
class CustomAkkaHttpServerProvider extends ServerProvider {

  def createServer(context: ServerProvider.Context): CustomAkkaHttpServer = {
    val serverContext = AkkaHttpServer.Context.fromServerProviderContext(context)
    new CustomAkkaHttpServer(serverContext)
  }
}
