package org.thp.thehive.services.notification.email.templates

import org.thp.scalligraph.models.Entity

case class Context(label: String, id: String)

object Context {
  def apply(entity: Option[Entity]): Context = entity.map(e => Context(e._model.label, e._id)).getOrElse(empty)

  def empty = Context("", "")
}
