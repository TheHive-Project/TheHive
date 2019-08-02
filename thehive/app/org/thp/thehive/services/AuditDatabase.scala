//package org.thp.thehive.services
//
//import javax.inject.Inject
//import org.apache.tinkerpop.gremlin.structure.Graph
//import org.apache.tinkerpop.gremlin.structure.Transaction.Status
//import org.thp.scalligraph.ParentProvider
//import org.thp.scalligraph.models.Database
//
//import scala.language.existentials
//import scala.util.{Success, Try}
//
//class AuditDatabase @Inject()(originalDatabase: ParentProvider[Database]) extends DatabaseWrapper(() => originalDatabase.get().get) {
//
//  override def tryTransaction[A](body: Graph => Try[A]): Try[A] =
//    super.tryTransaction { graph =>
//      val result           = body(graph)
//      val currentCallbacks = takeCallbacks(graph)
//      result.flatMap { r =>
//        currentCallbacks
//          .foldRight[Try[Unit]](Success(()))((cb, a) => a.flatMap(_ => cb()))
//          .map(_ => r)
//      }
//    }
//
////  override def transaction[A](body: Graph => A): A = super.transaction(body)
//
//}
