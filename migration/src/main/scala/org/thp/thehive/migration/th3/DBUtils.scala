package org.thp.thehive.migration.th3

import com.sksamuel.elastic4s.ElasticDsl.fieldSort
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.requests.searches.sort.{Sort, SortOrder}
import play.api.libs.json._

import scala.annotation.tailrec
import scala.collection.{AbstractIterator, AbstractView, Factory, SeqOps}
import scala.collection.generic.IsSeq
import scala.language.implicitConversions

object DBUtils {

  class DistinctByOperation[A](seqOps: SeqOps[A, Iterable, _]) {
    def distinctBy[B >: A, That](f: A => B)(implicit factory: Factory[B, That]): That =
      factory.fromSpecific(new AbstractView[B] {
        override def iterator: AbstractIterator[B] =
          new AbstractIterator[B] {
            var set: Set[B]         = Set[B]()
            val it: Iterator[A]     = seqOps.iterator
            def hasNext: Boolean    = nextElem.isDefined
            var nextElem: Option[A] = if (it.hasNext) Some(it.next()) else None
            @tailrec
            def getNext(): Unit =
              if (it.hasNext) {
                val a = it.next()
                val b = f(a)
                if (!set(b)) {
                  set += b
                  nextElem = Some(a)
                } else getNext()
              } else nextElem = None
            def next(): B = {
              val elem = nextElem
              getNext()
              elem.getOrElse(throw new NoSuchElementException)
            }
          }
      })
  }

  implicit def DistinctByOperation[Repr](coll: Repr)(implicit seq: IsSeq[Repr]): DistinctByOperation[seq.A] =
    new DistinctByOperation(seq(coll))

  def sortDefinition(sortBy: Seq[String]): Seq[Sort] = {
    val byFieldList: Seq[(String, Sort)] = sortBy
      .map {
        case f if f.startsWith("+") => f.drop(1) -> fieldSort(f.drop(1)).order(SortOrder.ASC)
        case f if f.startsWith("-") => f.drop(1) -> fieldSort(f.drop(1)).order(SortOrder.DESC)
        case f if f.nonEmpty        => f         -> fieldSort(f)
      }
    // then remove duplicates
    // Same as : val fieldSortDefs = byFieldList.groupBy(_._1).map(_._2.head).values.toSeq
    byFieldList.distinctBy(_._1).map(_._2)
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
      ("_type"        -> model) +
      ("_routing"     -> hit.routing.fold(id)(JsString.apply)) +
      ("_parent"      -> parent) +
      ("_id"          -> id) +
      ("_primaryTerm" -> JsNumber(hit.primaryTerm))
  }
}
