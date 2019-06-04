package org.thp.thehive.controllers.v0
import java.util.Date

import scala.language.implicitConversions

import play.api.libs.json.{JsNull, Json}

import io.scalaland.chimney.dsl._
import org.thp.scalligraph.{Output, RichOptionTry}
import org.thp.scalligraph.models.Entity
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{InputTask, OutputTask}
import org.thp.thehive.models.{Task, TaskStatus, TaskUser}
import org.thp.thehive.services.{TaskSrv, TaskSteps, UserSrv}

trait TaskConversion {
  val taskSrv: TaskSrv
  val userSrv: UserSrv

  implicit def fromInputTask(inputTask: InputTask): Task =
    inputTask
      .into[Task]
      .withFieldComputed(_.status, _.status.fold(TaskStatus.waiting)(TaskStatus.withName))
      .withFieldComputed(_.order, _.order.getOrElse(0))
      .transform

  implicit def toOutputTask(task: Task with Entity): Output[OutputTask] =
    Output[OutputTask](
      task
        .asInstanceOf[Task]
        .into[OutputTask]
        .withFieldConst(_.id, task._id)
        .withFieldConst(_._id, task._id)
        .withFieldComputed(_.status, _.status.toString)
        .transform
    )

  val taskProperties: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[TaskSteps]
      .property[String]("title")(_.simple.updatable)
      .property[Option[String]]("description")(_.simple.updatable)
      .property[String]("status")(_.simple.updatable)
      .property[Boolean]("flag")(_.simple.updatable)
      .property[Option[Date]]("startDate")(_.simple.updatable)
      .property[Option[Date]]("endDate")(_.simple.updatable)
      .property[Int]("order")(_.simple.updatable)
      .property[Option[Date]]("dueDate")(_.simple.updatable)
      .property[Option[String]]("owner")(_.derived(_.outTo[TaskUser].value[String]("login")).custom {
        (_, _, login: Option[String], vertex, _, graph, authContext) ⇒
          for {
            task ← taskSrv.get(vertex)(graph).getOrFail()
            user ← login.map(userSrv.get(_)(graph).getOrFail()).flip
          } yield user match {
            case Some(u) ⇒
              taskSrv.assign(task, u)(graph, authContext)
              Json.obj("owner" → u.login)
            case None ⇒
              Json.obj("owner" → JsNull)
          }
      })
      .build
}
