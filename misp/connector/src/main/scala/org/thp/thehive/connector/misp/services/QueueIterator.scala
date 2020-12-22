package org.thp.thehive.connector.misp.services

import akka.stream.scaladsl.SinkQueueWithCancel
import play.api.Logger

import java.util.NoSuchElementException
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.control.NonFatal

class QueueIterator[T](queue: SinkQueueWithCancel[T], readTimeout: Duration) extends Iterator[T] {
  lazy val logger: Logger = Logger(getClass)

  private var nextValue: Option[T] = None
  private var isFinished: Boolean  = false
  def getNextValue(): Unit =
    try nextValue = Await.result(queue.pull(), readTimeout)
    catch {
      case NonFatal(e) =>
        logger.error("Stream fails", e)
        isFinished = true
        nextValue = None
    }
  override def hasNext: Boolean =
    if (isFinished) false
    else {
      if (nextValue.isEmpty)
        getNextValue()
      nextValue.isDefined
    }

  override def next(): T =
    nextValue match {
      case Some(v) =>
        nextValue = None
        v
      case _ if !isFinished =>
        getNextValue()
        nextValue.getOrElse {
          isFinished = true
          throw new NoSuchElementException
        }
      case _ => throw new NoSuchElementException
    }
}

object QueueIterator {
  def apply[T](queue: SinkQueueWithCancel[T], readTimeout: Duration = 1.minute) = new QueueIterator[T](queue, readTimeout)
}
