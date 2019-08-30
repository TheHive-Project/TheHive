package org.thp.thehive.controllers.v0

import org.thp.thehive.dto.v0.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models.Organisation
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.models.{Entity, UniMapping}
import scala.language.implicitConversions

import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.services.OrganisationSteps

object OrganisationConversion {
  implicit def fromInputOrganisation(inputOrganisation: InputOrganisation): Organisation =
    inputOrganisation
      .into[Organisation]
      .transform

  implicit def toOutputOrganisation(organisation: Organisation with Entity): Output[OutputOrganisation] =
    Output(
      organisation
        .asInstanceOf[Organisation]
        .into[OutputOrganisation]
        .transform
    )

  val organisationProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[OrganisationSteps]
      .property("name", UniMapping.string)(_.simple.updatable)
      .build
}
