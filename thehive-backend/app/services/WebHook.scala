package services

import java.net.ConnectException
import javax.inject.Inject

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

import play.api.{ Configuration, Logger }
import play.api.libs.json.JsObject
import play.api.libs.ws.WSRequest

import org.elastic4play.services.AuxSrv

case class WebHook(name: String, ws: WSRequest)(implicit ec: ExecutionContext) {
  private[WebHook] lazy val logger = Logger(getClass.getName + "." + name)

  def send(obj: JsObject): Unit = ws.post(obj).onComplete {
    case Success(resp) if resp.status / 100 != 2 ⇒ logger.error(s"WebHook returns status ${resp.status} ${resp.statusText}")
    case Failure(_: ConnectException)            ⇒ logger.error(s"Connection to WebHook $name error")
    case Failure(error)                          ⇒ logger.error("WebHook call error", error)
    case _                                       ⇒
  }
}

class WebHooks(
    webhooks: Seq[WebHook],
    auxSrv: AuxSrv,
    implicit val ec: ExecutionContext) {
  @Inject() def this(
      configuration: Configuration,
      globalWS: CustomWSAPI,
      auxSrv: AuxSrv,
      ec: ExecutionContext) = {
    this(
      for {
        cfg ← configuration.getOptional[Configuration]("webhooks").toSeq
        whWS = globalWS.withConfig(cfg)
        name ← cfg.subKeys
        whConfig ← Try(cfg.get[Configuration](name)).toOption
        url ← whConfig.getOptional[String]("url")
        instanceWS = whWS.withConfig(whConfig).url(url)
      } yield WebHook(name, instanceWS)(ec),
      auxSrv,
      ec)
  }

  def send(obj: JsObject): Unit = {
    (for {
      objectType ← (obj \ "objectType").asOpt[String]
      objectId ← (obj \ "objectId").asOpt[String]
    } yield auxSrv(objectType, objectId, nparent = 0, withStats = false, removeUnaudited = false))
      .getOrElse(Future.successful(JsObject.empty))
      .map(o ⇒ obj + ("object" → o))
      .fallbackTo(Future.successful(obj))
      .foreach(o ⇒ webhooks.foreach(_.send(o)))
  }
}
