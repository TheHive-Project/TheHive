package org.thp.thehive.controllers.v0

import org.thp.thehive.dto.v0.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models.Organisation
import io.scalaland.chimney.dsl._
import scala.language.implicitConversions

trait OrganisationConversion {
  implicit def fromInputOrganisation(inputOrganisation: InputOrganisation): Organisation =
    inputOrganisation
      .into[Organisation]
      .transform

  implicit def toOutputOrganisation(organisation: Organisation): OutputOrganisation =
    organisation
      .into[OutputOrganisation]
      .transform
}
