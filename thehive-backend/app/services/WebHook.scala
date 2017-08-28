package services

import java.net.ConnectException
import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

import play.api.{ Configuration, Logger }
import play.api.libs.json.JsObject
import play.api.libs.ws.WSRequest

case class WebHook(name: String, ws: WSRequest)(implicit ec: ExecutionContext) {
  private[WebHook] lazy val logger = Logger(getClass.getName + "." + name)

  def send(obj: JsObject) = ws.post(obj).onComplete {
    case Success(resp) if resp.status / 100 != 2 ⇒ logger.error(s"WebHook returns status ${resp.status} ${resp.statusText}")
    case Failure(ce: ConnectException)           ⇒ logger.error(s"Connection to WebHook $name error", ce)
    case Failure(error)                          ⇒ logger.error("WebHook call error", error)
    case _                                       ⇒
  }
}

class WebHooks(
    webhooks: Seq[WebHook]) {
  @Inject() def this(
    configuration: Configuration,
    globalWS: CustomWSAPI,
    ec: ExecutionContext) = {
    this(
      for {
        cfg ← configuration.getOptional[Configuration]("webhooks").toSeq
        whWS = globalWS.withConfig(cfg)
        name ← cfg.subKeys
        whConfig ← Try(cfg.get[Configuration](name)).toOption
        url ← whConfig.getOptional[String]("url")
        instanceWS = whWS.withConfig(whConfig).url(url)
      } yield WebHook(name, instanceWS)(ec))
  }

  def send(obj: JsObject): Unit = webhooks.foreach(_.send(obj))
}
