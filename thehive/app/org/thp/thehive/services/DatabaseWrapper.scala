package org.thp.thehive.services

import java.util.Date
import java.util.function.Consumer

import akka.NotUsed
import akka.stream.scaladsl.Source
import gremlin.scala._
import javax.inject.Provider
import org.apache.tinkerpop.gremlin.structure.{Graph, Transaction}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.Model.Base
import org.thp.scalligraph.models._

import scala.reflect.runtime.{universe => ru}
import scala.util.Try

class DatabaseWrapper(dbProvider: Provider[Database]) extends Database {
  lazy val db: Database                                               = dbProvider.get()
  override lazy val createdAtMapping: SingleMapping[Date, _]          = db.createdAtMapping
  override lazy val createdByMapping: SingleMapping[String, String]   = db.createdByMapping
  override lazy val updatedAtMapping: OptionMapping[Date, _]          = db.updatedAtMapping
  override lazy val updatedByMapping: OptionMapping[String, String]   = db.updatedByMapping
  override lazy val binaryMapping: SingleMapping[Array[Byte], String] = db.binaryMapping

  override def close(): Unit = db.close()

  override def isValidId(id: String): Boolean = db.isValidId(id)

  override def createVertex[V <: Product](graph: Graph, authContext: AuthContext, model: Model.Vertex[V], v: V): V with Entity =
    db.createVertex(graph, authContext, model, v)

  override def createEdge[E <: Product, FROM <: Product, TO <: Product](
      graph: Graph,
      authContext: AuthContext,
      model: Model.Edge[E, FROM, TO],
      e: E,
      from: FROM with Entity,
      to: TO with Entity
  ): E with Entity = db.createEdge(graph, authContext, model, e, from, to)

  override def update[E <: Product](
      elementTraversal: GremlinScala[_ <: Element],
      fields: Seq[(String, Any)],
      model: Base[E],
      graph: Graph,
      authContext: AuthContext
  ): Try[Seq[E with Entity]] = db.update(elementTraversal, fields, model, graph, authContext)

  override def roTransaction[A](body: Graph => A): A                                                           = db.roTransaction(body)
  override def transaction[A](body: Graph => A): A                                                             = db.transaction(body)
  override def tryTransaction[A](body: Graph => Try[A]): Try[A]                                                = db.tryTransaction(body)
  override def source[A](query: Graph => Iterator[A]): Source[A, NotUsed]                                      = db.source(query)
  override def source[A, B](body: Graph => (Iterator[A], B)): (Source[A, NotUsed], B)                          = db.source(body)
  override def currentTransactionId(graph: Graph): AnyRef                                                      = db.currentTransactionId(graph)
  override def addCallback(callback: () => Try[Unit])(implicit graph: Graph): Unit                             = db.addCallback(callback)
  override def takeCallbacks(graph: Graph): List[() => Try[Unit]]                                              = db.takeCallbacks(graph)
  override def version(module: String): Int                                                                    = db.version(module)
  override def setVersion(module: String, v: Int): Try[Unit]                                                   = db.setVersion(module, v)
  override def getModel[E <: Product: ru.TypeTag]: Base[E]                                                     = db.getModel[E]
  override def getVertexModel[E <: Product: ru.TypeTag]: Model.Vertex[E]                                       = db.getVertexModel[E]
  override def getEdgeModel[E <: Product: ru.TypeTag, FROM <: Product, TO <: Product]: Model.Edge[E, FROM, TO] = db.getEdgeModel[E, FROM, TO]
  override def createSchemaFrom(schemaObject: Schema)(implicit authContext: AuthContext): Try[Unit]            = db.createSchemaFrom(schemaObject)(authContext)
  override def createSchema(model: Model, models: Model*): Try[Unit]                                           = db.createSchema(model, models: _*)
  override def createSchema(models: Seq[Model]): Try[Unit]                                                     = db.createSchema(models)
  override def addSchemaIndexes(schemaObject: Schema): Try[Unit]                                               = db.addSchemaIndexes(schemaObject)
  override def addSchemaIndexes(model: Model, models: Model*): Try[Unit]                                       = db.addSchemaIndexes(model, models: _*)
  override def addSchemaIndexes(models: Seq[Model]): Try[Unit]                                                 = db.addSchemaIndexes(models)
  override def enableIndexes(): Try[Unit]                                                                      = db.enableIndexes()
  override def removeAllIndexes(): Unit                                                                        = db.removeAllIndexes()
  override def addProperty[T](model: String, propertyName: String, mapping: Mapping[_, _, _]): Try[Unit] =
    db.addProperty(model, propertyName, mapping)
  override def removeProperty(model: String, propertyName: String, usedOnlyByThisModel: Boolean): Try[Unit] =
    db.removeProperty(model, propertyName, usedOnlyByThisModel)
  override def addIndex(model: String, indexType: IndexType.Value, properties: Seq[String]): Try[Unit] = db.addIndex(model, indexType, properties)
  override def drop(): Unit                                                                            = db.drop()

  override def getSingleProperty[D, G](element: Element, key: String, mapping: SingleMapping[D, G]): D = db.getSingleProperty(element, key, mapping)

  override def getOptionProperty[D, G](element: Element, key: String, mapping: OptionMapping[D, G]): Option[D] =
    db.getOptionProperty(element, key, mapping)
  override def getListProperty[D, G](element: Element, key: String, mapping: ListMapping[D, G]): Seq[D] = db.getListProperty(element, key, mapping)
  override def getSetProperty[D, G](element: Element, key: String, mapping: SetMapping[D, G]): Set[D]   = db.getSetProperty(element, key, mapping)
  override def getProperty[D](element: Element, key: String, mapping: Mapping[D, _, _]): D              = db.getProperty(element, key, mapping)

  override def setSingleProperty[D, G](element: Element, key: String, value: D, mapping: SingleMapping[D, _]): Unit =
    db.setSingleProperty[D, G](element, key, value, mapping)

  override def setOptionProperty[D, G](element: Element, key: String, value: Option[D], mapping: OptionMapping[D, _]): Unit =
    db.setOptionProperty[D, G](element, key, value, mapping)

  override def setListProperty[D, G](element: Element, key: String, values: Seq[D], mapping: ListMapping[D, _]): Unit =
    db.setListProperty[D, G](element, key, values, mapping)

  override def setSetProperty[D, G](element: Element, key: String, values: Set[D], mapping: SetMapping[D, _]): Unit =
    db.setSetProperty[D, G](element, key, values, mapping)
  override def setProperty[D](element: Element, key: String, value: D, mapping: Mapping[D, _, _]): Unit    = db.setProperty(element, key, value, mapping)
  override def labelFilter[E <: Element](model: Model): GremlinScala[E] => GremlinScala[E]                 = db.labelFilter(model)
  override def labelFilter[E <: Element](label: String): GremlinScala[E] => GremlinScala[E]                = db.labelFilter(label)
  override lazy val extraModels: Seq[Model]                                                                = db.extraModels
  override def addTransactionListener(listener: Consumer[Transaction.Status])(implicit graph: Graph): Unit = db.addTransactionListener(listener)
  override def mapPredicate[T](predicate: P[T]): P[T]                                                      = db.mapPredicate(predicate)
  override def toId(id: Any): Any                                                                          = db.toId(id)

  override val binaryLinkModel: Model.Edge[BinaryLink, Binary, Binary] = db.binaryLinkModel
  override val binaryModel: Model.Vertex[Binary]                       = db.binaryModel
}
