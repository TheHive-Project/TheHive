package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.connector.cortex.models.Job
import org.thp.thehive.connector.cortex.services.JobOps._
import org.thp.thehive.models.{RichCase, RichObservable}
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.ObservableOps._

import java.util.{Map => JMap}

trait JobRenderer {
  def jobParents(traversal: Traversal.V[Job])(implicit
      authContext: AuthContext
  ): Traversal[Option[(RichObservable, RichCase)], JMap[String, Any], Converter[Option[(RichObservable, RichCase)], JMap[String, Any]]] =
    traversal.observable.project(_.by(_.richObservable).by(_.`case`.richCase)).domainMap(Some(_))
}
