package org.thp.thehive.models

import org.thp.scalligraph.BuildVertexEntity

import java.util.Date

object ValueType extends Enumeration {
  val string, integer, float, boolean, date = Value
}

@BuildVertexEntity
case class KeyValue(
    namespace: String,
    predicate: String,
    level: String,
    `type`: ValueType.Value,
    string: Option[String],
    integer: Option[Int],
    float: Option[Double],
    boolean: Option[Boolean],
    date: Option[Date]
)
