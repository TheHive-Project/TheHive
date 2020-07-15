package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.steps.Traversal
import org.thp.thehive.connector.cortex.services.JobSteps
import org.thp.thehive.models.{RichCase, RichObservable}

trait JobRenderer {
  def jobParents(
      jobSteps: JobSteps
  )(implicit authContext: AuthContext): Traversal[Option[(RichObservable, RichCase)], Option[(RichObservable, RichCase)]] =
    jobSteps.observable.project(_.by(_.richObservable).by(_.`case`.richCase)).map(Some(_))
}
