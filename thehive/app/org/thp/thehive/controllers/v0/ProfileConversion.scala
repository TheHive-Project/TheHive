package org.thp.thehive.controllers.v0

import scala.language.implicitConversions

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.auth.Permission
import org.thp.scalligraph.controllers.Output
import org.thp.scalligraph.models.{Entity, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.thehive.dto.v0.{InputProfile, OutputProfile}
import org.thp.thehive.models.Profile
import org.thp.thehive.services.ProfileSteps

object ProfileConversion {
  implicit def toOutputProfile(profile: Profile with Entity): Output[OutputProfile] =
    Output[OutputProfile](
      profile
        .asInstanceOf[Profile]
        .into[OutputProfile]
        .withFieldConst(_._id, profile._id)
        .withFieldConst(_._updatedAt, profile._updatedAt)
        .withFieldConst(_._updatedBy, profile._updatedBy)
        .withFieldConst(_._createdAt, profile._createdAt)
        .withFieldConst(_._createdBy, profile._createdBy)
        .withFieldComputed(_.permissions, _.permissions.asInstanceOf[Set[String]].toSeq.sorted)
        .transform
    )

  implicit def fromInputProfile(inputProfile: InputProfile): Profile =
    inputProfile
      .into[Profile]
      .withFieldComputed(_.permissions, _.permissions.map(Permission.apply))
      .transform

  val profileProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ProfileSteps]
      .property("name", UniMapping.string)(_.simple.updatable)
      .property("permissions", UniMapping.string.set)(_.simple.updatable)
      .build
}
