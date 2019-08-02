//package org.thp.thehive.services
//
//import java.util.Date
//
//import gremlin.scala._
//import javax.inject.{Inject, Provider, Singleton}
//import org.thp.scalligraph.ParentProvider
//import org.thp.scalligraph.auth.AuthContext
//import org.thp.scalligraph.models.Model.Base
//import org.thp.scalligraph.models._
//import org.thp.scalligraph.services.EdgeSrv
//import org.thp.thehive.models.{Audit, Audited}
//
//import scala.reflect.runtime.{universe => ru}
//import scala.util.Try
//
//class DatabaseWrapper(dbProvider: Provider[Database]) extends Database {
//  lazy val db: Database                                               = dbProvider.get()
//  override lazy val createdAtMapping: SingleMapping[Date, _]          = db.createdAtMapping
//  override lazy val createdByMapping: SingleMapping[String, String]   = db.createdByMapping
//  override lazy val updatedAtMapping: OptionMapping[Date, _]          = db.updatedAtMapping
//  override lazy val updatedByMapping: OptionMapping[String, String]   = db.updatedByMapping
//  override lazy val binaryMapping: SingleMapping[Array[Byte], String] = db.binaryMapping
//
//  override def isValidId(id: String): Boolean = db.isValidId(id)
//
//  override def createVertex[V <: Product](graph: Graph, authContext: AuthContext, model: Model.Vertex[V], v: V): V with Entity =
//    db.createVertex(graph, authContext, model, v)
//
//  override def createEdge[E <: Product, FROM <: Product, TO <: Product](
//      graph: Graph,
//      authContext: AuthContext,
//      model: Model.Edge[E, FROM, TO],
//      e: E,
//      from: FROM with Entity,
//      to: TO with Entity
//  ): E with Entity = db.createEdge(graph, authContext, model, e, from, to)
//
//  override def update[E <: Product](
//      elementTraversal: GremlinScala[_ <: Element],
//      fields: Seq[(String, Any)],
//      model: Base[E],
//      graph: Graph,
//      authContext: AuthContext
//  ): Try[E with Entity] = db.update(elementTraversal, fields, model, graph, authContext)
//
//  override def readOnlyTransaction[A](body: Graph => A): A                                                     = db.readOnlyTransaction(body)
//  override def transaction[A](body: Graph => A): A                                                             = db.transaction(body)
//  override def tryTransaction[A](body: Graph => Try[A]): Try[A]                                                = db.tryTransaction(body)
//  override def currentTransactionId(graph: Graph): AnyRef                                                      = db.currentTransactionId(graph)
//  override def addCallback(callback: () => Try[Unit])(implicit graph: Graph): Unit                             = db.addCallback(callback)
//  override protected def takeCallbacks(graph: Graph): List[() => Try[Unit]]                                    = db.takeCallbacks(graph)
//  override def version(module: String): Int                                                                    = db.version(module)
//  override def setVersion(module: String, v: Int): Unit                                                        = db.setVersion(module, v)
//  override def getModel[E <: Product: ru.TypeTag]: Base[E]                                                     = db.getModel[E]
//  override def getVertexModel[E <: Product: ru.TypeTag]: Model.Vertex[E]                                       = db.getVertexModel[E]
//  override def getEdgeModel[E <: Product: ru.TypeTag, FROM <: Product, TO <: Product]: Model.Edge[E, FROM, TO] = db.getEdgeModel[E, FROM, TO]
//  override def createSchemaFrom(schemaObject: Schema)(implicit authContext: AuthContext): Try[Unit]            = db.createSchemaFrom(schemaObject)(authContext)
//  override def createSchema(model: Model, models: Model*): Try[Unit]                                           = db.createSchema(model, models: _*)
//  override def createSchema(models: Seq[Model]): Try[Unit]                                                     = db.createSchema(models)
//  override def drop(): Unit                                                                                    = db.drop()
//
//  override def getSingleProperty[D, G](element: Element, key: String, mapping: SingleMapping[D, G]): D = db.getSingleProperty(element, key, mapping)
//  override def getOptionProperty[D, G](element: Element, key: String, mapping: OptionMapping[D, G]): Option[D] =
//    db.getOptionProperty(element, key, mapping)
//  override def getListProperty[D, G](element: Element, key: String, mapping: ListMapping[D, G]): Seq[D] = db.getListProperty(element, key, mapping)
//  override def getSetProperty[D, G](element: Element, key: String, mapping: SetMapping[D, G]): Set[D]   = db.getSetProperty(element, key, mapping)
//  override def getProperty[D](element: Element, key: String, mapping: Mapping[D, _, _]): D              = db.getProperty(element, key, mapping)
//  override def setSingleProperty[D, G](element: Element, key: String, value: D, mapping: SingleMapping[D, _]): Unit =
//    db.setSingleProperty[D, G](element, key, value, mapping)
//  override def setOptionProperty[D, G](element: Element, key: String, value: Option[D], mapping: OptionMapping[D, _]): Unit =
//    db.setOptionProperty[D, G](element, key, value, mapping)
//  override def setListProperty[D, G](element: Element, key: String, values: Seq[D], mapping: ListMapping[D, _]): Unit =
//    db.setListProperty[D, G](element, key, values, mapping)
//  override def setSetProperty[D, G](element: Element, key: String, values: Set[D], mapping: SetMapping[D, _]): Unit =
//    db.setSetProperty[D, G](element, key, values, mapping)
//  override def setProperty[D](element: Element, key: String, value: D, mapping: Mapping[D, _, _]): Unit = db.setProperty(element, key, value, mapping)
//  override def labelFilter[E <: Element](model: Model): GremlinScala[E] => GremlinScala[E]              = db.labelFilter(model)
//  override lazy val extraModels: Seq[Model]                                                             = db.extraModels
//}
