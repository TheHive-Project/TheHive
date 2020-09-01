package org.thp.thehive.models

import org.thp.scalligraph.{BuildEdgeEntity, BuildVertexEntity}
import play.api.libs.json.JsValue

@BuildVertexEntity
case class Config(name: String, value: JsValue)

@BuildEdgeEntity[Organisation, Config]
case class OrganisationConfig()

@BuildEdgeEntity[User, Config]
case class UserConfig()
