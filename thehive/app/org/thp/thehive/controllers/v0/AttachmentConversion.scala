package org.thp.thehive.controllers.v0

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.controllers.Output
import org.thp.thehive.dto.v0.OutputAttachment
import org.thp.thehive.models.Attachment

object AttachmentConversion {
  implicit def toOutputAttachment(attachment: Attachment): Output[OutputAttachment] =
    Output[OutputAttachment](
      attachment
        .into[OutputAttachment]
        .withFieldComputed(_.hashes, _.hashes.map(_.toString).sortBy(_.length)(Ordering.Int.reverse))
        .withFieldComputed(_.id, _.attachmentId)
        .transform
    )
}
