package org.thp.thehive

import java.io.File

import play.api._
import play.core.server.{RealServerProcess, ServerConfig, ServerProcess, ServerProvider}

object DevStart extends App {
  override def main(args: Array[String]): Unit = {
    val process = new RealServerProcess(args)
    val config  = readConfig(process)

    val application: Application = {
      val environment = Environment(config.rootDir, process.classLoader, Mode.Dev)
      val context     = ApplicationLoader.Context.create(environment)
      val loader      = ApplicationLoader(context)
      loader.load(context)
    }
    Play.start(application)

    // Start the server
    val serverProvider = ServerProvider.fromConfiguration(process.classLoader, config.configuration)
    val server         = serverProvider.createServer(config, application)

    process.addShutdownHook {
      if (application.coordinatedShutdown.shutdownReason().isEmpty)
        server.stop()
    }
  }

  def readConfig(process: ServerProcess) = {
    val configuration: Configuration = {
      val rootDirArg    = process.args.headOption.map(new File(_))
      val rootDirConfig = rootDirArg.fold(Map.empty[String, String])(ServerConfig.rootDirConfig)
      Configuration.load(process.classLoader, process.properties, rootDirConfig, allowMissingApplicationConf = true)
    }
    val rootDir: File = {
      val path = configuration
        .getOptional[String]("play.server.dir")
        .getOrElse(sys.error("No root server path supplied"))
      val file = new File(path)
      if (!file.isDirectory)
        sys.error(s"Bad root server path: $path")
      file
    }

    def parsePort(portType: String): Option[Int] =
      configuration.getOptional[String](s"play.server.$portType.port").filter(_ != "disabled").map { str =>
        try Integer.parseInt(str)
        catch {
          case _: NumberFormatException =>
            sys.error(s"Invalid ${portType.toUpperCase} port: $str")
        }
      }

    val httpPort  = parsePort("http")
    val httpsPort = parsePort("https")
    val address   = configuration.getOptional[String]("play.server.http.address").getOrElse("0.0.0.0")

    if (httpPort.orElse(httpsPort).isEmpty)
      sys.error("Must provide either an HTTP or HTTPS port")

    ServerConfig(rootDir, httpPort, httpsPort, address, Mode.Dev, process.properties, configuration)
  }
}
