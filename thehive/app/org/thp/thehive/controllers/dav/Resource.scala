package org.thp.thehive.controllers.dav

import java.text.SimpleDateFormat
import java.util.Date

import org.thp.scalligraph.models.Entity
import org.thp.thehive.models.Attachment

import scala.xml.{Elem, Node}

trait Resource {
  def url: String
  def displayName: String
  def creationTime: Date
  def lastModified: Date
  def contentLength: Long
  def contentType: Option[String]
  def hasChildren: Boolean
  def etag: Option[String] = None

  protected def setNodeValue(prop: Node, value: Node): Option[Node] =
    prop match {
      case Elem(p, l, at, sc) => Some(Elem(p, l, at, sc, true, value))
    }

  protected def setValue(prop: Node, value: String): Option[Node] =
    setNodeValue(prop, scala.xml.Text(value))

  protected val formatter: SimpleDateFormat = {
    val df = new java.text.SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss z")
    df.setTimeZone(java.util.TimeZone.getTimeZone("GMT"))
    df
  }

  def property(prop: Node): Option[Node] = prop match {
    case p @ <displayname/>                 => setValue(p, displayName)
    case p @ <creationdate/>                => setValue(p, formatter.format(creationTime))
    case p @ <getlastmodified/>             => setValue(p, formatter.format(lastModified))
    case p @ <getcontentlength/>            => setValue(p, contentLength.toString)
    case p @ <resourcetype/> if hasChildren => setNodeValue(p, <D:collection/>)
    case p @ <resourcetype/>                => Some(p)
    case p @ <getetag/>                     => etag.flatMap(setValue(p, _))
    case p @ <getcontenttype/>              => contentType.flatMap(setValue(p, _))
    case _                                  => None
  }
}

case class StaticResource(url: String) extends Resource {
  override def displayName: String         = url
  override def creationTime: Date          = new Date()
  override def lastModified: Date          = new Date()
  override def contentLength: Long         = 0
  override def hasChildren: Boolean        = true
  override def contentType: Option[String] = None
}

case class EntityResource[E <: Entity](entity: E, id: String) extends Resource {
  override def url: String                 = id
  override def displayName: String         = url
  override def creationTime: Date          = entity._createdAt
  override def lastModified: Date          = entity._updatedAt.getOrElse(entity._createdAt)
  override def contentLength: Long         = 0
  override def hasChildren: Boolean        = true
  override def contentType: Option[String] = None
}

case class AttachmentResource(attachment: Attachment with Entity, emptyId: Boolean) extends Resource {
  override def url: String                 = if (emptyId) "" else attachment.attachmentId
  override def displayName: String         = attachment.name
  override def creationTime: Date          = attachment._createdAt
  override def lastModified: Date          = attachment._updatedAt.getOrElse(attachment._createdAt)
  override def contentLength: Long         = attachment.size
  override def hasChildren: Boolean        = false
  override def etag: Option[String]        = Some(attachment.attachmentId)
  override def contentType: Option[String] = Some(attachment.contentType)
}
