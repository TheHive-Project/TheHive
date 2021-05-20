package org.thp.thehive.connector.cortex.controllers.v0

import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.traversal.{Converter, Traversal}
import org.thp.thehive.connector.cortex.models.Job
import org.thp.thehive.connector.cortex.services.CortexOps
import org.thp.thehive.models.{RichCase, RichObservable}
import org.thp.thehive.services.TheHiveOpsNoDeps

import java.util.{Map => JMap}

trait JobRenderer extends TheHiveOpsNoDeps with CortexOps {
  def jobParents(traversal: Traversal.V[Job])(implicit
      authContext: AuthContext
  ): Traversal[Option[(RichObservable, RichCase)], JMap[String, Any], Converter[Option[(RichObservable, RichCase)], JMap[String, Any]]] =
    traversal.observable.project(_.by(_.richObservable).by(_.`case`.richCase)).domainMap(Some(_))
}
