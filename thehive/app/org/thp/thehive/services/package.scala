//package org.thp.thehive
//
//import org.thp.scalligraph.steps.VertexSteps
//
//package object services {
//
//  implicit class EntityStepsOps[E <: Product](steps: Traversal.V[Vertex][E]) {
//    def asCase: Traversal.V[Case] = steps match {
//      case caseSteps: Traversal.V[Case] => caseSteps
//      case _                    => new CaseSteps(steps.raw)(steps.db, steps.graph)
//    }
//    def asTask: Traversal.V[Task] = steps match {
//      case taskSteps: Traversal.V[Task] => taskSteps
//      case _                    => new TaskSteps(steps.raw)(steps.db, steps.graph)
//    }
//    def asLog: Traversal.V[Log] = steps match {
//      case logSteps: Traversal.V[Log] => logSteps
//      case _                  => new LogSteps(steps.raw)(steps.db, steps.graph)
//    }
//    def asObservable: Traversal.V[Observable] = steps match {
//      case observableSteps: Traversal.V[Observable] => observableSteps
//      case _                                => new ObservableSteps(steps.raw)(steps.db, steps.graph)
//    }
//    def asAlert: Traversal.V[Alert] = steps match {
//      case alertSteps: Traversal.V[Alert] => alertSteps
//      case _                      => new AlertSteps(steps.raw)(steps.db, steps.graph)
//    }
//  }
//}
