package org.thp.thehive.migration.th3

import com.sksamuel.elastic4s.http.ElasticDsl.fieldSort
import com.sksamuel.elastic4s.http.search.SearchHit
import com.sksamuel.elastic4s.searches.sort.Sort
import com.sksamuel.elastic4s.searches.sort.SortOrder.{ASC, DESC}
import play.api.libs.json._

import scala.collection.IterableLike
import scala.collection.generic.CanBuildFrom

object DBUtils {

  def distinctBy[A, B, Repr, That](xs: IterableLike[A, Repr])(f: A => B)(implicit cbf: CanBuildFrom[Repr, A, That]): That = {
    val builder = cbf(xs.repr)
    val i       = xs.iterator
    var set     = Set[B]()
    while (i.hasNext) {
      val o = i.next
      val b = f(o)
      if (!set(b)) {
        set += b
        builder += o
      }
    }
    builder.result
  }

  def sortDefinition(sortBy: Seq[String]): Seq[Sort] = {
    val byFieldList: Seq[(String, Sort)] = sortBy
      .map {
        case f if f.startsWith("+") => f.drop(1) -> fieldSort(f.drop(1)).order(ASC)
        case f if f.startsWith("-") => f.drop(1) -> fieldSort(f.drop(1)).order(DESC)
        case f if f.nonEmpty        => f         -> fieldSort(f)
      }
    // then remove duplicates
    // Same as : val fieldSortDefs = byFieldList.groupBy(_._1).map(_._2.head).values.toSeq
    distinctBy(byFieldList)(_._1).map(_._2)
  }

  /**
    * Transform search hit into JsObject
    * This function parses hit source add _type, _routing, _parent, _id and _version attributes
    */
  def hit2json(hit: SearchHit): JsObject = {
    val id   = JsString(hit.id)
    val body = Json.parse(hit.sourceAsString).as[JsObject]
    val (parent, model) = (body \ "relations" \ "parent").asOpt[JsString] match {
      case Some(p) => p      -> (body \ "relations" \ "name").as[JsString]
      case None    => JsNull -> (body \ "relations").as[JsString]
    }
    body - "relations" +
      ("_type"    -> model) +
      ("_routing" -> hit.routing.fold(id)(JsString.apply)) +
      ("_parent"  -> parent) +
      ("_id"      -> id) +
      ("_version" -> JsNumber(hit.version))
  }
}
