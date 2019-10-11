package org.thp.thehive

import org.thp.scalligraph.steps.VertexSteps

package object services {

  implicit class EntityStepsOps[E <: Product](steps: VertexSteps[E]) {
    def asCase       = new CaseSteps(steps.raw)(steps.db, steps.graph)
    def asTask       = new TaskSteps(steps.raw)(steps.db, steps.graph)
    def asLog        = new LogSteps(steps.raw)(steps.db, steps.graph)
    def asObservable = new ObservableSteps(steps.raw)(steps.db, steps.graph)
    def asAlert      = new AlertSteps(steps.raw)(steps.db, steps.graph)
  }
}
