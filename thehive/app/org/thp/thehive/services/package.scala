package org.thp.thehive

import org.thp.scalligraph.steps.VertexSteps

package object services {

  implicit class EntityStepsOps[E <: Product](steps: VertexSteps[E]) {
    def asCase: CaseSteps = steps match {
      case caseSteps: CaseSteps => caseSteps
      case _                    => new CaseSteps(steps.raw)(steps.db, steps.graph)
    }
    def asTask: TaskSteps = steps match {
      case taskSteps: TaskSteps => taskSteps
      case _                    => new TaskSteps(steps.raw)(steps.db, steps.graph)
    }
    def asLog: LogSteps = steps match {
      case logSteps: LogSteps => logSteps
      case _                  => new LogSteps(steps.raw)(steps.db, steps.graph)
    }
    def asObservable: ObservableSteps = steps match {
      case observableSteps: ObservableSteps => observableSteps
      case _                                => new ObservableSteps(steps.raw)(steps.db, steps.graph)
    }
    def asAlert: AlertSteps = steps match {
      case alertSteps: AlertSteps => alertSteps
      case _                      => new AlertSteps(steps.raw)(steps.db, steps.graph)
    }
  }
}
