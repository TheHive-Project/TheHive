package org.thp.thehive.shell

import ch.qos.logback.classic.{Level, LoggerContext}
import org.slf4j.LoggerFactory

object LogAccess {
  def setLevel(packageName: String, levelName: String): Unit = {
    val level = levelName match {
      case "ALL"   => Level.ALL
      case "DEBUG" => Level.DEBUG
      case "INFO"  => Level.INFO
      case "WARN"  => Level.WARN
      case "ERROR" => Level.ERROR
      case "OFF"   => Level.OFF
      case "TRACE" => Level.TRACE
      case _       => Level.INFO
    }
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val logger        = loggerContext.getLogger(packageName)
    logger.setLevel(level)
  }

  def enableProfile: Unit      = setLevel("org.thp.scalligraph.traversal.Profile", "TRACE")
  def disableProfile: Unit     = setLevel("org.thp.scalligraph.traversal.Profile", "OFF")
  def enableGremlin: Unit      = setLevel("org.thp.scalligraph.traversal.Gremlin", "TRACE")
  def disableGremlin: Unit     = setLevel("org.thp.scalligraph.traversal.Gremlin", "OFF")
  def enableByteCode: Unit     = setLevel("org.thp.scalligraph.traversal.ByteCode", "TRACE")
  def disableByteCode: Unit    = setLevel("org.thp.scalligraph.traversal.ByteCode", "OFF")
  def enableStrategies: Unit   = setLevel("org.thp.scalligraph.traversal.Strategies", "TRACE")
  def disableStrategies: Unit  = setLevel("org.thp.scalligraph.traversal.Strategies", "OFF")
  def enableExplain: Unit      = setLevel("org.thp.scalligraph.traversal.Explain", "TRACE")
  def disableExplain: Unit     = setLevel("org.thp.scalligraph.traversal.Explain", "OFF")
  def enableTransaction: Unit  = setLevel("org.thp.scalligraph.models.Database", "TRACE")
  def disableTransaction: Unit = setLevel("org.thp.scalligraph.models.Database", "OFF")
}
