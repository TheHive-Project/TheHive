package org.thp.thehive.controllers.dav

import java.text.SimpleDateFormat
import java.util.Date

import scala.xml.{Elem, Node}

import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.Attachment

trait Resource {
  def url: String
  def lastModified: Date
  def contentLength: Long
  def hasChildren: Boolean

  protected def easyNode(prop: Node, value: Node): Option[Node] =
    prop match {
      case Elem(p, l, at, sc) => Some(Elem(p, l, at, sc, true, value))
    }

  protected def easy(prop: Node, value: String): Option[Node] =
    easyNode(prop, scala.xml.Text(value))

  protected val formatter: SimpleDateFormat = {
    val df = new java.text.SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss z")
    df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
    df
  }

  def property(prop: Node): Option[Node] = prop match {
    case p @ <getlastmodified/>             => easy(p, formatter.format(lastModified))
    case p @ <getcontentlength/>            => easy(p, contentLength.toString)
    case p @ <resourcetype/> if hasChildren => easyNode(p, <D:collection/>)
    case p @ <resourcetype/>                => Some(p)
    case _                                  => None
  }
}

case class StaticResource(url: String) extends Resource {
  override def lastModified: Date   = new Date()
  override def contentLength: Long  = 0
  override def hasChildren: Boolean = true
}

case class EntityResource(entity: Entity, emptyId: Boolean) extends Resource {
  override def url: String          = if (emptyId) "" else entity._id
  override def lastModified: Date   = entity._updatedAt.getOrElse(entity._createdAt)
  override def contentLength: Long  = 0
  override def hasChildren: Boolean = true
}

case class AttachmentResource(attachment: Attachment with Entity, emptyId: Boolean) extends Resource {
  override def url: String          = if (emptyId) "" else attachment.attachmentId
  override def lastModified: Date   = attachment._updatedAt.getOrElse(attachment._createdAt)
  override def contentLength: Long  = attachment.size
  override def hasChildren: Boolean = false
}
