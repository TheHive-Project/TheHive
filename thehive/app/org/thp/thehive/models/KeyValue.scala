package org.thp.thehive.models
import java.util.Date

import org.thp.scalligraph.VertexEntity

object ValueType extends Enumeration {
  val string, integer, float, boolean, date = Value
}

@VertexEntity
case class KeyValue(
    name: String,
    `type`: ValueType.Value,
    string: Option[String],
    integer: Option[Int],
    float: Option[Float],
    boolean: Option[Boolean],
    date: Option[Date])
