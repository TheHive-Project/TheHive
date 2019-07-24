package org.thp.thehive.controllers.v0

import org.thp.thehive.dto.v0.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models.Organisation
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.models.UniMapping

import scala.language.implicitConversions
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.services.OrganisationSteps

object OrganisationConversion {
  implicit def fromInputOrganisation(inputOrganisation: InputOrganisation): Organisation =
    inputOrganisation
      .into[Organisation]
      .transform

  implicit def toOutputOrganisation(organisation: Organisation): OutputOrganisation =
    organisation
      .into[OutputOrganisation]
      .transform

  val organisationProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[OrganisationSteps]
      .property("name", UniMapping.stringMapping)(_.simple.updatable)
      .build
}
