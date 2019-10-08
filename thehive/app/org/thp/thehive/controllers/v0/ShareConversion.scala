package org.thp.thehive.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.thehive.dto.v0.OutputShare
import org.thp.thehive.models.RichShare

object ShareConversion {

  implicit def toOutputShare(share: RichShare): Output[OutputShare] =
    Output[OutputShare](
      share
        .into[OutputShare]
        .withFieldComputed(_._id, _.share._id)
        .withFieldComputed(_.createdAt, _.share._createdAt)
        .withFieldComputed(_.createdBy, _.share._createdBy)
        .transform
    )
}
