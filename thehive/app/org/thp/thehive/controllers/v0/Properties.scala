package org.thp.thehive.controllers.v0

import gremlin.scala.{__, By, Key, P, Vertex}
import javax.inject.{Inject, Singleton}
import org.scalactic.Accumulation._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{NoValue, PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.scalligraph.steps.IdMapping
import org.thp.scalligraph.steps.StepsOps._
import org.thp.scalligraph.{AttributeCheckingError, BadRequestError, InvalidFormatAttributeError, RichSeq}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputTask
import org.thp.thehive.models.{AlertCase, CaseStatus, Permissions, TaskStatus}
import org.thp.thehive.services.{
  AlertSrv,
  AlertSteps,
  AuditSteps,
  CaseSrv,
  CaseSteps,
  CaseTemplateSrv,
  CaseTemplateSteps,
  CustomFieldSrv,
  CustomFieldSteps,
  DashboardSrv,
  DashboardSteps,
  LogSteps,
  ObservableSrv,
  ObservableSteps,
  OrganisationSteps,
  PageSteps,
  ProfileSrv,
  ProfileSteps,
  RoleSrv,
  TagSteps,
  TaskSrv,
  TaskSteps,
  UserSrv,
  UserSteps
}
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

@Singleton
class Properties @Inject()(
    caseSrv: CaseSrv,
    userSrv: UserSrv,
    alertSrv: AlertSrv,
    dashboardSrv: DashboardSrv,
    observableSrv: ObservableSrv,
    caseTemplateSrv: CaseTemplateSrv,
    taskSrv: TaskSrv,
    profileSrv: ProfileSrv,
    roleSrv: RoleSrv,
    customFieldSrv: CustomFieldSrv
) {

  lazy val alert: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[AlertSteps]
      .property("type", UniMapping.string)(_.field.updatable)
      .property("source", UniMapping.string)(_.field.updatable)
      .property("sourceRef", UniMapping.string)(_.field.updatable)
      .property("title", UniMapping.string)(_.field.updatable)
      .property("description", UniMapping.string)(_.field.updatable)
      .property("severity", UniMapping.int)(_.field.updatable)
      .property("date", UniMapping.date)(_.field.updatable)
      .property("lastSyncDate", UniMapping.date.optional)(_.field.updatable)
      .property("tags", UniMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            alertSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(alert => alertSrv.updateTagNames(alert, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.field.updatable)
      .property("tlp", UniMapping.int)(_.field.updatable)
      .property("pap", UniMapping.int)(_.field.updatable)
      .property("read", UniMapping.boolean)(_.field.updatable)
      .property("follow", UniMapping.boolean)(_.field.updatable)
      .property("status", UniMapping.string)(
        _.select(
          _.project(
            _.apply(By(Key[Boolean]("read")))
              .and(By(__[Vertex].outToE[AlertCase].limit(1).count()))
          ).map {
            case (false, caseCount) if caseCount == 0 => "New"
            case (false, _)                           => "Updated"
            case (true, caseCount) if caseCount == 0  => "Ignored"
            case (true, _)                            => "Imported"
          }
        ).readonly
      )
      .property("summary", UniMapping.string.optional)(_.field.updatable)
      .property("user", UniMapping.string)(_.field.updatable)
      .property("customFields", UniMapping.identity[JsValue])(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), alertSteps) => alertSteps.customFields(name).jsonValue.map(_._2)
        case (_, alertSteps)                                => alertSteps.customFields.jsonValue.fold.map(l => JsObject(l.asScala))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- alertSrv.getOrFail(vertex)(graph)
            _ <- alertSrv.setOrCreateCustomField(c, name, Some(value))(graph, authContext)
          } yield Json.obj(s"customField.$name" -> value)
        case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
          for {
            c   <- alertSrv.get(vertex)(graph).getOrFail()
            cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(n)(graph).map(_ -> v) }
            _   <- alertSrv.updateCustomField(c, cfv)(graph, authContext)
          } yield Json.obj("customFields" -> values)

        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })(NoValue(JsNull))
      .property("case", IdMapping)(_.select(_.`case`._id).readonly)
      .build

  lazy val audit: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[AuditSteps]
      .property("operation", UniMapping.string)(_.rename("action").readonly)
      .property("details", UniMapping.string)(_.field.readonly)
      .property("objectType", UniMapping.string.optional)(_.field.readonly)
      .property("objectId", UniMapping.string.optional)(_.field.readonly)
      .property("base", UniMapping.boolean)(_.rename("mainAction").readonly)
      .property("startDate", UniMapping.date)(_.rename("_createdAt").readonly)
      .property("requestId", UniMapping.string)(_.field.readonly)
      .property("rootId", IdMapping)(_.select(_.context._id).readonly)
      .build

  lazy val `case`: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseSteps]
      .property("caseId", UniMapping.int)(_.rename("number").readonly)
      .property("title", UniMapping.string)(_.field.updatable)
      .property("description", UniMapping.string)(_.field.updatable)
      .property("severity", UniMapping.int)(_.field.updatable)
      .property("startDate", UniMapping.date)(_.field.updatable)
      .property("endDate", UniMapping.date.optional)(_.field.updatable)
      .property("tags", UniMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(`case` => caseSrv.updateTagNames(`case`, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.field.updatable)
      .property("tlp", UniMapping.int)(_.field.updatable)
      .property("pap", UniMapping.int)(_.field.updatable)
      .property("status", UniMapping.enum(CaseStatus))(_.field.updatable)
      .property("summary", UniMapping.string.optional)(_.field.updatable)
      .property("owner", UniMapping.string.optional)(_.select(_.user.login).custom { (_, login, vertex, _, graph, authContext) =>
        for {
          c    <- caseSrv.get(vertex)(graph).getOrFail()
          user <- login.map(userSrv.get(_)(graph).getOrFail()).flip
          _ <- user match {
            case Some(u) => caseSrv.assign(c, u)(graph, authContext)
            case None    => caseSrv.unassign(c)(graph, authContext)
          }
        } yield Json.obj("owner" -> user.map(_.login))
      })
      .property("resolutionStatus", UniMapping.string.optional)(_.select(_.resolutionStatus.value).custom {
        (_, resolutionStatus, vertex, _, graph, authContext) =>
          for {
            c <- caseSrv.get(vertex)(graph).getOrFail()
            _ <- resolutionStatus match {
              case Some(s) => caseSrv.setResolutionStatus(c, s)(graph, authContext)
              case None    => caseSrv.unsetResolutionStatus(c)(graph, authContext)
            }
          } yield Json.obj("resolutionStatus" -> resolutionStatus)
      })
      .property("impactStatus", UniMapping.string.optional)(_.select(_.impactStatus.value).custom {
        (_, impactStatus, vertex, _, graph, authContext) =>
          for {
            c <- caseSrv.getOrFail(vertex)(graph)
            _ <- impactStatus match {
              case Some(s) => caseSrv.setImpactStatus(c, s)(graph, authContext)
              case None    => caseSrv.unsetImpactStatus(c)(graph, authContext)
            }
          } yield Json.obj("impactStatus" -> impactStatus)
      })
      .property("customFields", UniMapping.identity[JsValue])(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), caseSteps) => caseSteps.customFields(name).jsonValue.map(_._2)
        case (_, caseSteps)                                => caseSteps.customFields.jsonValue.fold.map(l => JsObject(l.asScala))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- caseSrv.getOrFail(vertex)(graph)
            _ <- caseSrv.setOrCreateCustomField(c, name, Some(value))(graph, authContext)
          } yield Json.obj(s"customField.$name" -> value)
        case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
          for {
            c   <- caseSrv.get(vertex)(graph).getOrFail()
            cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(n)(graph).map(_ -> v) }
            _   <- caseSrv.updateCustomField(c, cfv)(graph, authContext)
          } yield Json.obj("customFields" -> values)
        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })(NoValue(JsNull))
      .property("computed.handlingDurationInHours", UniMapping.long)(
        _.select(
          _.coalesce(
            _.has("endDate")
              .sack((_: Long, endDate: Long) => endDate, By(Key[Long]("endDate")))
              .sack((_: Long) - (_: Long), By(Key[Long]("startDate")))
              .sack((_: Long) / (_: Long), By(__.constant(3600000L)))
              .sack[Long](),
            _.constant(0L)
          )
        ).readonly
      )
      .build

  lazy val caseTemplate: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseTemplateSteps]
      .property("name", UniMapping.string)(_.field.readonly)
      .property("displayName", UniMapping.string)(_.field.updatable)
      .property("titlePrefix", UniMapping.string.optional)(_.field.updatable)
      .property("description", UniMapping.string.optional)(_.field.updatable)
      .property("severity", UniMapping.int.optional)(_.field.updatable)
      .property("tags", UniMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseTemplateSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(caseTemplate => caseTemplateSrv.updateTagNames(caseTemplate, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.field.updatable)
      .property("tlp", UniMapping.int.optional)(_.field.updatable)
      .property("pap", UniMapping.int.optional)(_.field.updatable)
      .property("summary", UniMapping.string.optional)(_.field.updatable)
      .property("user", UniMapping.string)(_.field.updatable)
      .property("customFields", UniMapping.identity[JsValue])(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), caseTemplateSteps) => caseTemplateSteps.customFields(name).jsonValue.map(_._2)
        case (_, caseTemplateSteps)                                => caseTemplateSteps.customFields.jsonValue.fold.map(l => JsObject(l.asScala))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- caseTemplateSrv.getOrFail(vertex)(graph)
            _ <- caseTemplateSrv.setOrCreateCustomField(c, name, Some(value))(graph, authContext)
          } yield Json.obj(s"customFields.$name" -> value)
        case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
          for {
            c   <- caseTemplateSrv.get(vertex)(graph).getOrFail()
            cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(n)(graph).map(_ -> v) }
            _   <- caseTemplateSrv.updateCustomField(c, cfv)(graph, authContext)
          } yield Json.obj("customFields" -> values)
        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })(NoValue(JsNull))
      .property("tasks", UniMapping.identity[JsValue].sequence)(_.select(_.tasks.richTask.map(_.toJson)).custom {
        (_, value, vertex, _, graph, authContext) =>
          val fp = FieldsParser[InputTask]

          caseTemplateSrv.get(vertex)(graph).tasks.remove()
          for {
            caseTemplate <- caseTemplateSrv.getOrFail(vertex)(graph)
            tasks        <- value.validatedBy(t => fp(Field(t))).badMap(AttributeCheckingError(_)).toTry
            createdTasks <- tasks
              .toTry(t => t.owner.map(userSrv.getOrFail(_)(graph)).flip.flatMap(owner => taskSrv.create(t.toTask, owner)(graph, authContext)))
            _ <- createdTasks.toTry(t => caseTemplateSrv.addTask(caseTemplate, t.task)(graph, authContext))
          } yield Json.obj("tasks" -> createdTasks.map(_.toJson))
      })(NoValue(JsNull))
      .build

  lazy val customField: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CustomFieldSteps]
      .property("name", UniMapping.string)(_.field.readonly)
      .property("description", UniMapping.string)(_.field.updatable)
      .property("reference", UniMapping.string)(_.field.readonly)
      .property("mandatory", UniMapping.boolean)(_.field.updatable)
      .property("type", UniMapping.string)(_.field.readonly)
      .property("options", UniMapping.json.sequence)(_.field.updatable)
      .build

  lazy val dashboard: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[DashboardSteps]
      .property("title", UniMapping.string)(_.field.updatable)
      .property("description", UniMapping.string)(_.field.updatable)
      .property("definition", UniMapping.string)(_.field.updatable)
      .property("status", UniMapping.string)(
        _.select(_.shared.map(shared => if (shared) "Shared" else "Private")).custom { // TODO replace by choose step
          case (_, "Shared", vertex, _, graph, authContext) =>
            for {
              d <- dashboardSrv.get(vertex)(graph).getOrFail()
              _ <- dashboardSrv.shareUpdate(d, status = true)(graph, authContext)
            } yield Json.obj("status" -> "Shared")

          case (_, "Private", vertex, _, graph, authContext) =>
            for {
              d <- dashboardSrv.get(vertex)(graph).getOrFail()
              _ <- dashboardSrv.shareUpdate(d, status = false)(graph, authContext)
            } yield Json.obj("status" -> "Private")

          case (_, "Deleted", vertex, _, graph, authContext) =>
            for {
              d <- dashboardSrv.get(vertex)(graph).getOrFail()
              _ <- dashboardSrv.remove(d)(graph, authContext)
            } yield Json.obj("status" -> "Deleted")

          case (_, status, _, _, _, _) =>
            Failure(InvalidFormatAttributeError("status", "String", Set("Shared", "Private", "Deleted"), FString(status)))
        }
      )
      .build

  lazy val log: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[LogSteps]
      .property("message", UniMapping.string)(_.field.updatable)
      .property("deleted", UniMapping.boolean)(_.field.updatable)
      .property("startDate", UniMapping.date)(_.rename("date").readonly)
      .property("status", UniMapping.string)(_.field.readonly)
      .property("attachment", IdMapping)(_.select(_.attachments._id).readonly)
      .build

  lazy val observable: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ObservableSteps]
      .property("status", UniMapping.string)(_.select(_.constant("Ok")).readonly)
      .property("startDate", UniMapping.date)(_.select(_._createdAt).readonly)
      .property("ioc", UniMapping.boolean)(_.field.updatable)
      .property("sighted", UniMapping.boolean)(_.field.updatable)
      .property("tags", UniMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            observableSrv
              .getOrFail(vertex)(graph)
              .flatMap(observable => observableSrv.updateTagNames(observable, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("message", UniMapping.string)(_.field.updatable)
      .property("tlp", UniMapping.int)(_.field.updatable)
      .property("dataType", UniMapping.string)(_.select(_.observableType.name).readonly)
      .property("data", UniMapping.string.optional)(_.select(_.data.data).readonly)
      // TODO add attachment ?
      .build

  lazy val organisation: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[OrganisationSteps]
      .property("name", UniMapping.string)(_.field.updatable)
      .property("description", UniMapping.string)(_.field.updatable)
      .build

  lazy val page: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[PageSteps]
      .property("title", UniMapping.string)(_.field.updatable)
      .property("content", UniMapping.string.set)(_.field.updatable)
      .build

  lazy val profile: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ProfileSteps]
      .property("name", UniMapping.string)(_.field.updatable)
      .property("permissions", UniMapping.string.set)(_.field.updatable)
      .build

  lazy val tag: List[PublicProperty[_, _]] = PublicPropertyListBuilder[TagSteps]
    .property("namespace", UniMapping.string)(_.field.readonly)
    .property("predicate", UniMapping.string)(_.field.readonly)
    .property("value", UniMapping.string.optional)(_.field.readonly)
    .property("description", UniMapping.string.optional)(_.field.readonly)
    .build

  lazy val task: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[TaskSteps]
      .property("title", UniMapping.string)(_.field.updatable)
      .property("description", UniMapping.string.optional)(_.field.updatable)
      .property("status", UniMapping.enum(TaskStatus))(_.field.custom { (_, value, vertex, _, graph, authContext) =>
        for {
          task <- taskSrv.get(vertex)(graph).getOrFail()
          user <- userSrv
            .current(graph, authContext)
            .getOrFail()
          _ <- taskSrv.updateStatus(task, user, value)(graph, authContext)
        } yield Json.obj("status" -> value)
      })
      .property("flag", UniMapping.boolean)(_.field.updatable)
      .property("startDate", UniMapping.date.optional)(_.field.updatable)
      .property("endDate", UniMapping.date.optional)(_.field.updatable)
      .property("order", UniMapping.int)(_.field.updatable)
      .property("dueDate", UniMapping.date.optional)(_.field.updatable)
      .property("group", UniMapping.string)(_.field.updatable)
      .property("owner", UniMapping.string.optional)(
        _.select(_.user.login)
          .custom { (_, login: Option[String], vertex, _, graph, authContext) =>
            for {
              task <- taskSrv.get(vertex)(graph).getOrFail()
              user <- login.map(userSrv.getOrFail(_)(graph)).flip
              _ <- user match {
                case Some(u) => taskSrv.assign(task, u)(graph, authContext)
                case None    => taskSrv.unassign(task)(graph, authContext)
              }
            } yield Json.obj("owner" -> user.map(_.login))
          }
      )
      .build

  lazy val user: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[UserSteps]
      .property("login", UniMapping.string)(_.field.readonly)
      .property("name", UniMapping.string)(_.field.custom { (_, value, vertex, db, graph, authContext) =>
        def isCurrentUser: Try[Unit] =
          userSrv
            .current(graph, authContext)
            .get(vertex)
            .existsOrFail()

        def isUserAdmin: Try[Unit] =
          userSrv
            .current(graph, authContext)
            .organisations(Permissions.manageUser)
            .users
            .get(vertex)
            .existsOrFail()

        isCurrentUser
          .orElse(isUserAdmin)
          .map { _ =>
            db.setProperty(vertex, "name", value, UniMapping.string)
            Json.obj("name" -> value)
          }
      })
      .property("status", UniMapping.string)(
        _.select(_.choose(predicate = _.locked.is(P.eq(true)), onTrue = _.constant("Locked"), onFalse = _.constant("Ok")))
          .custom { (_, value, vertex, db, graph, authContext) =>
            userSrv
              .current(graph, authContext)
              .organisations(Permissions.manageUser)
              .users
              .get(vertex)
              .existsOrFail()
              .flatMap {
                case _ if value == "Ok" =>
                  db.setProperty(vertex, "locked", false, UniMapping.boolean)
                  Success(Json.obj("status" -> value))
                case _ if value == "Locked" =>
                  db.setProperty(vertex, "locked", true, UniMapping.boolean)
                  Success(Json.obj("status" -> value))
                case _ => Failure(InvalidFormatAttributeError("status", "UserStatus", Set("Ok", "Locked"), FString(value)))
              }
          }
      )
      .build
}
