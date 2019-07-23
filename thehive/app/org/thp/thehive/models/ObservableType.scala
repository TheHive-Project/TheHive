package org.thp.thehive.models

import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[Observable, ObservableType]
case class ObservableObservableType()

@VertexEntity
case class ObservableType(name: String, isAttachment: Boolean)
