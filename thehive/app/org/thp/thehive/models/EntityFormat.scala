package org.thp.thehive.models

import org.thp.scalligraph.models.Entity
import play.api.libs.json._

object EntityFormat {
  implicit val writes: Writes[Entity] = new Writes[Entity] {
    override def writes(e: Entity): JsValue = e match {
      case t: Task => Json.toJson(t)
    }
  }
}
