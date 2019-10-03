package org.thp.thehive.models

import play.api.libs.json.JsValue

import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@VertexEntity
case class Config(name: String, value: JsValue)

@EdgeEntity[Organisation, Config]
case class OrganisationConfig()

@EdgeEntity[User, Config]
case class UserConfig()
