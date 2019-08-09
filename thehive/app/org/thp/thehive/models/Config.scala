package org.thp.thehive.models

import org.thp.scalligraph.{EdgeEntity, VertexEntity}
import play.api.libs.json.JsValue

@VertexEntity
case class Config(name: String, value: JsValue)

@EdgeEntity[Organisation, Config]
case class OrganisationConfig()

@EdgeEntity[User, Config]
case class UserConfig()
