package org.thp.thehive.models

import org.thp.scalligraph.{EdgeEntity, VertexEntity}

@EdgeEntity[Log, Task]
case class LogTask()

@VertexEntity
case class Log(message: String)
