package org.thp.thehive

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.services.config.ConfigItem
import play.api.libs.json.{Format, JsResult, JsValue}

import scala.util.Try

case class TestConfigItem[B, F](value: F) extends ConfigItem[B, F] {
  override val get: F = value

  override val path: String        = ""
  override val description: String = ""
  override val defaultValue: B     = null.asInstanceOf[B]

  override val jsonFormat: Format[B] = new Format[B] {
    override def reads(json: JsValue): JsResult[B] = ???
    override def writes(o: B): JsValue             = ???
  }
  override def set(v: B)(implicit authContext: AuthContext): Try[Unit] = ???
  override def validation(v: B): Try[B]                                = ???
  override def getJson: JsValue                                        = ???
  override def onUpdate(f: (B, B) => Unit): Unit                       = ???
}
