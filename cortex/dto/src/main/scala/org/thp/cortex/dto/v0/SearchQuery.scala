package org.thp.cortex.dto.v0

import play.api.libs.json.{JsObject, Json, Writes}

case class SearchQuery(
    field: String,
    value: String,
    range: String,
    queryOverride: Option[JsObject] = None
)

object SearchQuery {
  implicit val writes: Writes[SearchQuery] = (o: SearchQuery) =>
    o.queryOverride
      .getOrElse(
        Json.obj(
          "query" -> Json.obj(
            "_field" -> o.field,
            "_value" -> o.value
          )
        )
      ) ++ Json.obj("range" -> o.range)
}
