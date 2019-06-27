package org.thp.cortex.dto.v0

import play.api.libs.json.{Json, Writes}

case class SearchQuery(
    field: String,
    value: String,
    range: String
)

object SearchQuery {
  implicit val writes: Writes[SearchQuery] = (o: SearchQuery) ⇒
    Json.obj(
      "query" → Json.obj(
        "_field" → o.field,
        "_value" → o.value
      ),
      "range" → o.range
    )
}
