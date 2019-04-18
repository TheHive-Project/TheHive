package org.thp.thehive.controllers.v1

import java.util.Date

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.thehive.dto.v1.{InputAlert, OutputAlert}
import org.thp.thehive.models.{Alert, RichAlert}

trait AlertConversion extends CustomFieldConversion {
  implicit def toOutputAlert(richAlert: RichAlert): Output[OutputAlert] =
    Output[OutputAlert](
      richAlert
        .into[OutputAlert]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .transform)

  implicit def fromInputAlert(inputAlert: InputAlert): Alert =
    inputAlert
      .into[Alert]
      .withFieldComputed(_.severity, _.severity.getOrElse(2))
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.pap, _.pap.getOrElse(2))
      .withFieldConst(_.read, false)
      .withFieldConst(_.lastSyncDate, new Date)
      .withFieldConst(_.follow, true)
      .transform

}
