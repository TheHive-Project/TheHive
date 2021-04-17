package org.thp.thehive.migration
import play.api.Logger

import java.io.{PrintWriter, StringWriter}
import scala.util.Try

class ProgressBar(terminal: Terminal, message: String, max: Int) {
  private var isDisplayed   = false
  private var current: Long = 0
  private val isTTY         = Try(terminal.getWidth()).isSuccess
  lazy val logger: Logger   = Logger(getClass)

  def inc(increment: Int = 1, extraMessage: String = ""): Unit = {
    current += increment

    val now = System.currentTimeMillis()

    val percent     = current * 100 / max
    val status      = s"[$current/$max:$percent%]"
    val fullMessage = s"$message $extraMessage".replaceAll("[\n\r]", "")
    logger.info(s"$now $current/$max $fullMessage ")
    if (isTTY) {
      val width         = terminal.getWidth()
      val progressWidth = width - status.length - 2
      val currentWidth  = (current * progressWidth / max).toInt
      val progress      = "=" * currentWidth + " " * (progressWidth - currentWidth)
      if (isDisplayed) {
        terminal.up(2)
        terminal.left(width)
        terminal.clearScreen(0)
      } else isDisplayed = true
      terminal.println(fullMessage)
      terminal.println(s"[$progress]$status")
    } else terminal.println(s"$status $fullMessage")
    terminal.flush()
  }

  def message(s: String): Unit = {
    if (isTTY && isDisplayed) {
      terminal.up(2)
      terminal.left(terminal.getWidth())
      terminal.clearScreen(0)
    }
    isDisplayed = false
    terminal.println(s)
  }

  def message(s: String, t: Throwable): Unit = {
    val errors = new StringWriter
    t.printStackTrace(new PrintWriter(errors))
    message(s"$s\n$errors")
  }
}
