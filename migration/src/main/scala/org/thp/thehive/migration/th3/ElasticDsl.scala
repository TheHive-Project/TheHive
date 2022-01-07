package org.thp.thehive.migration.th3

import play.api.libs.json.{JsNumber, JsObject, JsString, JsValue, Json}

object ElasticDsl {
  def searchQuery(query: JsObject, sort: String*): JsObject = {
    val order = JsObject(sort.collect {
      case f if f.startsWith("+") => f.drop(1) -> JsString("asc")
      case f if f.startsWith("-") => f.drop(1) -> JsString("desc")
      case f if f.nonEmpty        => f         -> JsString("asc")
    })
    Json.obj("query" -> query, "sort" -> order)
  }
  val matchAll: JsObject                                            = Json.obj("match_all" -> JsObject.empty)
  def termQuery(field: String, value: String): JsObject             = Json.obj("term" -> Json.obj(field -> value))
  def termsQuery(field: String, values: Iterable[String]): JsObject = Json.obj("terms" -> Json.obj(field -> values))
  def idsQuery(ids: String*): JsObject                              = Json.obj("ids" -> Json.obj("values" -> ids))
  def range[N](field: String, from: Option[N], to: Option[N])(implicit ev: N => BigDecimal) =
    Json.obj(
      "range" -> Json.obj(
        field -> JsObject(
          from.map(f => "gte" -> JsNumber(f)).toSeq ++
            to.map(t => "lt" -> JsNumber(t)).toSeq
        )
      )
    )
  def and(queries: JsValue*): JsObject = bool(queries)
  def or(queries: JsValue*): JsObject  = bool(Nil, queries)
  def bool(mustQueries: Seq[JsValue], shouldQueries: Seq[JsValue] = Nil, notQueries: Seq[JsValue] = Nil): JsObject =
    Json.obj(
      "bool" -> Json.obj(
        "must"     -> mustQueries,
        "should"   -> shouldQueries,
        "must_not" -> notQueries
      )
    )
  def hasParentQuery(parentType: String, query: JsObject): JsObject =
    Json.obj(
      "has_parent" -> Json.obj(
        "parent_type" -> parentType,
        "query"       -> query
      )
    )
}
