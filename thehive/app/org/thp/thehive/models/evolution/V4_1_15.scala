package org.thp.thehive.models.evolution

import org.apache.tinkerpop.gremlin.process.traversal.P
import org.thp.scalligraph.janus.JanusDatabase
import org.thp.scalligraph.models.{IndexType, Operations}

import scala.util.{Success, Try}

trait V4_1_15 extends TraversalOps {
  def evolutionV4_1_15: Operations => Operations =
    _.removeIndex("Tag", IndexType.unique, "namespace", "predicate", "value")
      .removeIndex("Alert", IndexType.unique, "type", "source", "sourceRef", "organisationId")
      .removeIndex("Organisation", IndexType.unique, "name")
      .removeIndex("Customfield", IndexType.unique, "name")
      .removeIndex("Profile", IndexType.unique, "name")
      .removeIndex("ImpactStatus", IndexType.unique, "value")
      .removeIndex("ObservableType", IndexType.unique, "name")
      .removeIndex("User", IndexType.unique, "login")
      .removeIndex("Case", IndexType.unique, "number")
      .removeIndex("ResolutionStatus", IndexType.unique, "value")
}
