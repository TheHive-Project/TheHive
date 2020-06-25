package org.thp.thehive.connector.cortex

import org.thp.scalligraph.models.Database
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.connector.cortex.models.ObservableJob
import org.thp.thehive.services.ObservableSteps

package object services {
  implicit class RichObservableSteps(observableSteps: ObservableSteps) {
    def jobs: JobSteps =
      new JobSteps(observableSteps.outTo[ObservableJob].raw)(observableSteps.db.asInstanceOf[Database], observableSteps.graph)
  }
}
