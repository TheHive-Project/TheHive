package org.thp.thehive.services

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.Element
import org.thp.scalligraph.models.{FullTextPredicate, IndexType, Schema, TextPredicate}
import org.thp.scalligraph.traversal.{Converter, Traversal, TraversalOps}

class SearchSrv(schema: Schema) extends TraversalOps {

  private def isStringField(fieldName: String): Boolean =
    schema.modelList.exists(_.fields.get(fieldName).exists(_.graphTypeClass == classOf[String]))
  val acceptedIndexTypes = Set(IndexType.standard, IndexType.fulltext, IndexType.fulltextOnly)

  val modelIndexedFields: Map[String, Map[IndexType, Set[String]]] = schema
    .modelList
    .map { model =>
      (
        model.label,
        model
          .indexes
          .filter(i => acceptedIndexTypes.contains(i._1))
          .groupMapReduce(_._1)(_._2.filter(isStringField).toSet)(_ ++ _)
      )
    }
    .toMap
  val anyModelIndexedFields: Map[IndexType, Set[String]] = modelIndexedFields.values.flatten.groupMapReduce(_._1)(_._2)(_ ++ _)

  def apply[D, G <: Element, C <: Converter[D, G]](query: String): Traversal[D, G, C] => Traversal[D, G, C] = {
    val filters = anyModelIndexedFields
      .getOrElse(IndexType.standard, Set.empty)
      .toSeq
      .flatMap(f =>
        Seq(
          (_: Traversal[D, G, C]).unsafeHas(f, TextPredicate.startsWith(query)),
          (_: Traversal[D, G, C]).unsafeHas(f, P.eq(query))
        )
      ) ++
      anyModelIndexedFields
        .getOrElse(IndexType.fulltext, Set.empty)
        .toSeq
        .flatMap(f =>
          Seq(
            (_: Traversal[D, G, C]).unsafeHas(f, FullTextPredicate.contains(query)),
            (_: Traversal[D, G, C]).unsafeHas(f, TextPredicate.startsWith(query)),
            (_: Traversal[D, G, C]).unsafeHas(f, P.eq(query))
          )
        ) ++
      anyModelIndexedFields
        .getOrElse(IndexType.fulltextOnly, Set.empty)
        .toSeq
        .map(f => (_: Traversal[D, G, C]).unsafeHas(f, FullTextPredicate.contains(query)))
    if (filters.isEmpty)
      (_: Traversal[D, G, C]).empty
    else
      (_: Traversal[D, G, C]).or(filters: _*)
  }

  def apply[D, G <: Element, C <: Converter[D, G]](model: String, query: String): Traversal[D, G, C] => Traversal[D, G, C] = {
    val filters = modelIndexedFields
      .getOrElse(model, Map.empty)
      .getOrElse(IndexType.standard, Set.empty)
      .toSeq
      .flatMap(f =>
        Seq(
          (_: Traversal[D, G, C]).unsafeHas(f, TextPredicate.startsWith(query)),
          (_: Traversal[D, G, C]).unsafeHas(f, P.eq(query))
        )
      ) ++
      modelIndexedFields
        .getOrElse(model, Map.empty)
        .getOrElse(IndexType.fulltext, Set.empty)
        .toSeq
        .flatMap(f =>
          Seq(
            (_: Traversal[D, G, C]).unsafeHas(f, FullTextPredicate.contains(query)),
            (_: Traversal[D, G, C]).unsafeHas(f, TextPredicate.startsWith(query)),
            (_: Traversal[D, G, C]).unsafeHas(f, P.eq(query))
          )
        ) ++
      modelIndexedFields
        .getOrElse(model, Map.empty)
        .getOrElse(IndexType.fulltextOnly, Set.empty)
        .toSeq
        .map(f => (_: Traversal[D, G, C]).unsafeHas(f, FullTextPredicate.contains(query)))
    if (filters.isEmpty)
      (_: Traversal[D, G, C]).empty
    else
      (_: Traversal[D, G, C]).or(filters: _*)
  }
}
