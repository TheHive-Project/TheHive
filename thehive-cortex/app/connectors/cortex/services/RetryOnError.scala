package connectors.cortex.services

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

import akka.actor.ActorSystem

object RetryOnError {
  def apply[A](cond: Throwable ⇒ Boolean = _ ⇒ true, maxRetry: Int = 5, initialDelay: FiniteDuration = 1.second)(body: ⇒ Future[A])(implicit system: ActorSystem, ec: ExecutionContext): Future[A] = {
    body.recoverWith {
      case e if maxRetry > 0 && cond(e) ⇒
        val resultPromise = Promise[A]
        system.scheduler.scheduleOnce(initialDelay) {
          resultPromise.completeWith(apply(cond, maxRetry - 1, initialDelay * 2)(body))
        }
        resultPromise.future
    }
  }
}
