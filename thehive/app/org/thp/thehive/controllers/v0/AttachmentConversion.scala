package org.thp.thehive.controllers.v0

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.thehive.dto.v0.OutputAttachment
import org.thp.thehive.models.Attachment

trait AttachmentConversion {
  implicit def toOutputAttachment(attachment: Attachment): Output[OutputAttachment] =
    Output[OutputAttachment](
      attachment
        .into[OutputAttachment]
        .withFieldComputed(_.hashes, _.hashes.map(_.toString).sortBy(_.length)(Ordering.Int.reverse))
        .withFieldComputed(_.id, _.attachmentId)
        .transform
    )
}
