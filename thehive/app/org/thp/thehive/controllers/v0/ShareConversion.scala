package org.thp.thehive.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.Entity
import org.thp.thehive.dto.v0.OutputShare
import org.thp.thehive.models.Share

object ShareConversion {

  def toOutputShare(share: Share with Entity, caseId: String, profile: String): Output[OutputShare] =
    Output[OutputShare](
      share
        .into[OutputShare]
        .withFieldConst(_.profile, profile)
        .withFieldConst(_.caseId, caseId)
        .withFieldComputed(_._id, _._id)
        .withFieldComputed(_.createdAt, _._createdAt)
        .withFieldComputed(_.createdBy, _._createdBy)
        .transform
    )
}
