package org.thp.thehive.controllers.v1

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.thehive.dto.v1.{InputOrganisation, OutputOrganisation}
import org.thp.thehive.models.Organisation
import scala.language.implicitConversions

import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.services.OrganisationSteps

trait OrganisationConversion {
  implicit def fromInputOrganisation(inputOrganisation: InputOrganisation): Organisation =
    inputOrganisation
      .into[Organisation]
      .transform

  implicit def toOutputOrganisation(organisation: Organisation): Output[OutputOrganisation] =
    Output[OutputOrganisation](
      organisation
        .into[OutputOrganisation]
        .transform
    )

  val organisationProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[OrganisationSteps]
      .property[String]("name")(_.simple.updatable)
      .build
}
