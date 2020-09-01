package org.thp.thehive.controllers.v0

import java.lang.{Long => JLong}
import java.util.Date

import javax.inject.{Inject, Singleton}
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.scalactic.Accumulation._
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, IdMapping, UMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.traversal.Converter
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{AttributeCheckingError, AuthorizationError, BadRequestError, InvalidFormatAttributeError, RichSeq}
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.dto.v0.InputTask
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.AuditOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.CustomFieldOps._
import org.thp.thehive.services.DashboardOps._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.OrganisationOps._
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.util.{Failure, Success, Try}

@Singleton
class Properties @Inject() (
    caseSrv: CaseSrv,
    userSrv: UserSrv,
    alertSrv: AlertSrv,
    dashboardSrv: DashboardSrv,
    observableSrv: ObservableSrv,
    caseTemplateSrv: CaseTemplateSrv,
    taskSrv: TaskSrv,
    customFieldSrv: CustomFieldSrv,
    db: Database
) {

  lazy val alert: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Alert]
      .property("type", UMapping.string)(_.field.updatable)
      .property("source", UMapping.string)(_.field.updatable)
      .property("sourceRef", UMapping.string)(_.field.updatable)
      .property("title", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .property("severity", UMapping.int)(_.field.updatable)
      .property("date", UMapping.date)(_.field.updatable)
      .property("lastSyncDate", UMapping.date.optional)(_.field.updatable)
      .property("tags", UMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            alertSrv
              .get(vertex)(graph)
              .getOrFail("Alert")
              .flatMap(alert => alertSrv.updateTagNames(alert, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("pap", UMapping.int)(_.field.updatable)
      .property("read", UMapping.boolean)(_.field.updatable)
      .property("follow", UMapping.boolean)(_.field.updatable)
      .property("status", UMapping.string)(
        _.select(
          _.project(
            _.byValue(_.read)
              .by(_.`case`.limit(1).count)
          ).domainMap {
            case (false, caseCount) if caseCount == 0L => "New"
            case (false, _)                            => "Updated"
            case (true, caseCount) if caseCount == 0L  => "Ignored"
            case (true, _)                             => "Imported"
          }
        ).readonly
      )
      .property("summary", UMapping.string.optional)(_.field.updatable)
      .property("user", UMapping.string)(_.field.updatable)
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), alertSteps) =>
          alertSteps.customFields(name).jsonValue
        case (_, alertSteps) => alertSteps.customFields.nameJsonValue.fold.domainMap(JsObject(_))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- alertSrv.getByIds(vertex.id.toString)(graph).getOrFail("Alert")
            _ <- alertSrv.setOrCreateCustomField(c, name, Some(value))(graph, authContext)
          } yield Json.obj(s"customField.$name" -> value)
        case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
          for {
            c   <- alertSrv.get(vertex)(graph).getOrFail("Alert")
            cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(n)(graph).map(_ -> v) }
            _   <- alertSrv.updateCustomField(c, cfv)(graph, authContext)
          } yield Json.obj("customFields" -> values)

        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })
      .property("case", IdMapping)(_.select(_.`case`._id).readonly)
      .build

  lazy val audit: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Audit]
      .property("operation", UMapping.string)(_.rename("action").readonly)
      .property("details", UMapping.string)(_.field.readonly)
      .property("objectType", UMapping.string.optional)(_.field.readonly)
      .property("objectId", UMapping.string.optional)(_.field.readonly)
      .property("base", UMapping.boolean)(_.rename("mainAction").readonly)
      .property("startDate", UMapping.date)(_.rename("_createdAt").readonly)
      .property("requestId", UMapping.string)(_.field.readonly)
      .property("rootId", IdMapping)(_.select(_.context._id).readonly)
      .build

  lazy val `case`: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Case]
      .property("caseId", UMapping.int)(_.rename("number").readonly)
      .property("title", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .property("severity", UMapping.int)(_.field.updatable)
      .property("startDate", UMapping.date)(_.field.updatable)
      .property("endDate", UMapping.date.optional)(_.field.updatable)
      .property("tags", UMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseSrv
              .get(vertex)(graph)
              .getOrFail("Case")
              .flatMap(`case` => caseSrv.updateTagNames(`case`, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("pap", UMapping.int)(_.field.updatable)
      .property("status", UMapping.enum[CaseStatus.type])(_.field.updatable)
      .property("summary", UMapping.string.optional)(_.field.updatable)
      .property("owner", UMapping.string.optional)(_.select(_.user.value(_.login)).custom { (_, login, vertex, _, graph, authContext) =>
        for {
          c    <- caseSrv.get(vertex)(graph).getOrFail("Case")
          user <- login.map(userSrv.get(_)(graph).getOrFail("User")).flip
          _ <- user match {
            case Some(u) => caseSrv.assign(c, u)(graph, authContext)
            case None    => caseSrv.unassign(c)(graph, authContext)
          }
        } yield Json.obj("owner" -> user.map(_.login))
      })
      .property("resolutionStatus", UMapping.string.optional)(_.select(_.resolutionStatus.value(_.value)).custom {
        (_, resolutionStatus, vertex, _, graph, authContext) =>
          for {
            c <- caseSrv.get(vertex)(graph).getOrFail("Case")
            _ <- resolutionStatus match {
              case Some(s) => caseSrv.setResolutionStatus(c, s)(graph, authContext)
              case None    => caseSrv.unsetResolutionStatus(c)(graph, authContext)
            }
          } yield Json.obj("resolutionStatus" -> resolutionStatus)
      })
      .property("impactStatus", UMapping.string.optional)(_.select(_.impactStatus.value(_.value)).custom {
        (_, impactStatus, vertex, _, graph, authContext) =>
          for {
            c <- caseSrv.getByIds(vertex.id.toString)(graph).getOrFail("Case")
            _ <- impactStatus match {
              case Some(s) => caseSrv.setImpactStatus(c, s)(graph, authContext)
              case None    => caseSrv.unsetImpactStatus(c)(graph, authContext)
            }
          } yield Json.obj("impactStatus" -> impactStatus)
      })
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), caseSteps) =>
          caseSteps
            .customFields(name)
            .jsonValue
        case (_, caseSteps) => caseSteps.customFields.nameJsonValue.fold.domainMap(JsObject(_))
      }
        .filter {
          case (FPathElem(_, FPathElem(name, _)), caseTraversal) =>
            db
              .roTransaction(implicit graph => customFieldSrv.get(name).value(_.`type`).getOrFail("CustomField"))
              .map {
                case CustomFieldType.boolean => caseTraversal.customFields(name).value(_.booleanValue)
                case CustomFieldType.date    => caseTraversal.customFields(name).value(_.dateValue)
                case CustomFieldType.float   => caseTraversal.customFields(name).value(_.floatValue)
                case CustomFieldType.integer => caseTraversal.customFields(name).value(_.integerValue)
                case CustomFieldType.string  => caseTraversal.customFields(name).value(_.stringValue)
              }
              .getOrElse(caseTraversal.constant2(null))
          case (_, caseTraversal) => caseTraversal.constant2(null)
        }
        .converter {
          case FPathElem(_, FPathElem(name, _)) =>
            db
              .roTransaction { implicit graph =>
                customFieldSrv.get(name).value(_.`type`).getOrFail("CustomField")
              }
              .map {
                case CustomFieldType.boolean => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[Boolean] }
                case CustomFieldType.date    => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[Date] }
                case CustomFieldType.float   => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[Double] }
                case CustomFieldType.integer => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[Long] }
                case CustomFieldType.string  => new Converter[Any, JsValue] { def apply(x: JsValue): Any = x.as[String] }
              }
              .getOrElse(new Converter[Any, JsValue] { def apply(x: JsValue): Any = x })
          case _ => (x: JsValue) => x
        }
        .custom {
          case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
            for {
              c <- caseSrv.getByIds(vertex.id.toString)(graph).getOrFail("Case")
              _ <- caseSrv.setOrCreateCustomField(c, name, Some(value), None)(graph, authContext)
            } yield Json.obj(s"customField.$name" -> value)
          case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
            for {
              c   <- caseSrv.get(vertex)(graph).getOrFail("Case")
              cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(n)(graph).map(cf => (cf, v, None)) }
              _   <- caseSrv.updateCustomField(c, cfv)(graph, authContext)
            } yield Json.obj("customFields" -> values)
          case _ => Failure(BadRequestError("Invalid custom fields format"))
        })
      .property("computed.handlingDurationInHours", UMapping.long)(
        _.select(
          _.coalesce(
            _.has("endDate")
              .sack(
                (_: JLong, endDate: JLong) => endDate,
                _.by(_.value(_.endDate).graphMap[Long, JLong, Converter[Long, JLong]](_.getTime, Converter.long))
              )
              .sack((_: Long) - (_: JLong), _.by(_.value(_.startDate).graphMap[Long, JLong, Converter[Long, JLong]](_.getTime, Converter.long)))
              .sack((_: Long) / (_: Long), _.by(_.constant(3600000L)))
              .sack[Long],
            _.constant(0L)
          )
        ).readonly
      )
      .build

  lazy val caseTemplate: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseTemplate]
      .property("name", UMapping.string)(_.field.updatable)
      .property("displayName", UMapping.string)(_.field.updatable)
      .property("titlePrefix", UMapping.string.optional)(_.field.updatable)
      .property("description", UMapping.string.optional)(_.field.updatable)
      .property("severity", UMapping.int.optional)(_.field.updatable)
      .property("tags", UMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseTemplateSrv
              .get(vertex)(graph)
              .getOrFail("CaseTemplate")
              .flatMap(caseTemplate => caseTemplateSrv.updateTagNames(caseTemplate, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("tlp", UMapping.int.optional)(_.field.updatable)
      .property("pap", UMapping.int.optional)(_.field.updatable)
      .property("summary", UMapping.string.optional)(_.field.updatable)
      .property("user", UMapping.string)(_.field.updatable)
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), caseTemplateSteps) => caseTemplateSteps.customFields(name).jsonValue
        case (_, caseTemplateSteps)                                => caseTemplateSteps.customFields.nameJsonValue.fold.domainMap(JsObject(_))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- caseTemplateSrv.getByIds(vertex.id.toString)(graph).getOrFail("CaseTemplate")
            _ <- caseTemplateSrv.setOrCreateCustomField(c, name, Some(value), None)(graph, authContext)
          } yield Json.obj(s"customFields.$name" -> value)
        case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
          for {
            c   <- caseTemplateSrv.get(vertex)(graph).getOrFail("CaseTemplate")
            cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(n)(graph).map(_ -> v) }
            _   <- caseTemplateSrv.updateCustomField(c, cfv)(graph, authContext)
          } yield Json.obj("customFields" -> values)
        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })
      .property("tasks", UMapping.jsonNative.sequence)(_.select(_.tasks.richTask.domainMap(_.toJson)).custom { //  FIXME select the correct mapping
        (_, value, vertex, _, graph, authContext) =>
          val fp = FieldsParser[InputTask]

          caseTemplateSrv.get(vertex)(graph).tasks.remove()
          for {
            caseTemplate <- caseTemplateSrv.getByIds(vertex.id.toString)(graph).getOrFail("CaseTemplate")
            tasks        <- value.validatedBy(t => fp(Field(t))).badMap(AttributeCheckingError(_)).toTry
            createdTasks <-
              tasks
                .toTry(t => t.owner.map(userSrv.getOrFail(_)(graph)).flip.flatMap(owner => taskSrv.create(t.toTask, owner)(graph, authContext)))
            _ <- createdTasks.toTry(t => caseTemplateSrv.addTask(caseTemplate, t.task)(graph, authContext))
          } yield Json.obj("tasks" -> createdTasks.map(_.toJson))
      })
      .build

  lazy val customField: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CustomField]
      .property("name", UMapping.string)(_.rename("displayName").updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .property("reference", UMapping.string)(_.rename("name").readonly)
      .property("mandatory", UMapping.boolean)(_.field.updatable)
      .property("type", UMapping.string)(_.field.readonly)
      .property("options", UMapping.json.sequence)(_.field.updatable)
      .build

  lazy val dashboard: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Dashboard]
      .property("title", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .property("definition", UMapping.string)(_.field.updatable)
      .property("status", UMapping.string)(
        _.select(_.organisation.fold.domainMap(d => if (d.isEmpty) "Private" else "Shared")).custom { // TODO replace by choose step
          case (_, "Shared", vertex, _, graph, authContext) =>
            for {
              dashboard <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
              _         <- dashboardSrv.share(dashboard, authContext.organisation, writable = false)(graph, authContext)
            } yield Json.obj("status" -> "Shared")

          case (_, "Private", vertex, _, graph, authContext) =>
            for {
              d <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
              _ <- dashboardSrv.unshare(d, authContext.organisation)(graph, authContext)
            } yield Json.obj("status" -> "Private")

          case (_, "Deleted", vertex, _, graph, authContext) =>
            for {
              d <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
              _ <- dashboardSrv.remove(d)(graph, authContext)
            } yield Json.obj("status" -> "Deleted")

          case (_, status, _, _, _, _) =>
            Failure(InvalidFormatAttributeError("status", "String", Set("Shared", "Private", "Deleted"), FString(status)))
        }
      )
      .build

  lazy val log: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Log]
      .property("message", UMapping.string)(_.field.updatable)
      .property("deleted", UMapping.boolean)(_.field.updatable)
      .property("startDate", UMapping.date)(_.rename("date").readonly)
      .property("status", UMapping.string)(_.select(_.constant("Ok")).readonly)
      .property("attachment", IdMapping)(_.select(_.attachments._id).readonly)
      .build

  lazy val observable: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Observable]
      .property("status", UMapping.string)(_.select(_.constant("Ok")).readonly)
      .property("startDate", UMapping.date)(_.select(_._createdAt).readonly)
      .property("ioc", UMapping.boolean)(_.field.updatable)
      .property("sighted", UMapping.boolean)(_.field.updatable)
      .property("tags", UMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            observableSrv
              .getByIds(vertex.id.toString)(graph)
              .getOrFail("Observable")
              .flatMap(observable => observableSrv.updateTagNames(observable, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("message", UMapping.string)(_.field.updatable)
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("dataType", UMapping.string)(_.select(_.observableType.value(_.name)).readonly)
      .property("data", UMapping.string.optional)(_.select(_.data.value(_.data)).readonly)
      .property("attachment.name", UMapping.string.optional)(_.select(_.attachments.value(_.name)).readonly)
      .property("attachment.size", UMapping.long.optional)(_.select(_.attachments.value(_.size)).readonly)
      .property("attachment.contentType", UMapping.string.optional)(_.select(_.attachments.value(_.contentType)).readonly)
      .property("attachment.hashes", UMapping.hash)(_.select(_.attachments.value(_.hashes)).readonly)
      .build

  lazy val organisation: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Organisation]
      .property("name", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .build

  lazy val page: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Page]
      .property("title", UMapping.string)(_.field.updatable)
      .property("content", UMapping.string.set)(_.field.updatable)
      .build

  lazy val profile: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Profile]
      .property("name", UMapping.string)(_.field.updatable)
      .property("permissions", UMapping.string.set)(_.field.updatable)
      .build

  lazy val tag: List[PublicProperty[_, _]] = PublicPropertyListBuilder[Tag]
    .property("namespace", UMapping.string)(_.field.readonly)
    .property("predicate", UMapping.string)(_.field.readonly)
    .property("value", UMapping.string.optional)(_.field.readonly)
    .property("description", UMapping.string.optional)(_.field.readonly)
    .property("text", UMapping.string)(_.select(_.displayName).readonly)
    .build

  lazy val task: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[Task]
      .property("title", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string.optional)(_.field.updatable)
      .property("status", UMapping.enum[TaskStatus.type])(_.field.custom { (_, value, vertex, _, graph, authContext) =>
        for {
          task <- taskSrv.get(vertex)(graph).getOrFail("Task")
          user <-
            userSrv
              .current(graph, authContext)
              .getOrFail("User")
          _ <- taskSrv.updateStatus(task, user, value)(graph, authContext)
        } yield Json.obj("status" -> value)
      })
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("startDate", UMapping.date.optional)(_.field.updatable)
      .property("endDate", UMapping.date.optional)(_.field.updatable)
      .property("order", UMapping.int)(_.field.updatable)
      .property("dueDate", UMapping.date.optional)(_.field.updatable)
      .property("group", UMapping.string)(_.field.updatable)
      .property("owner", UMapping.string.optional)(
        _.select(_.assignee.value(_.login))
          .custom { (_, login: Option[String], vertex, _, graph, authContext) =>
            for {
              task <- taskSrv.get(vertex)(graph).getOrFail("Task")
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
    PublicPropertyListBuilder[User]
      .property("login", UMapping.string)(_.field.readonly)
      .property("name", UMapping.string)(_.field.custom { (_, value, vertex, db, graph, authContext) =>
        def isCurrentUser: Try[Unit] =
          userSrv
            .current(graph, authContext)
            .getByIds(vertex.id.toString)
            .existsOrFail

        def isUserAdmin: Try[Unit] =
          userSrv
            .current(graph, authContext)
            .organisations(Permissions.manageUser)(db)
            .users
            .getByIds(vertex.id.toString)
            .existsOrFail

        isCurrentUser
          .orElse(isUserAdmin)
          .map { _ =>
            UMapping.string.setProperty(vertex, "name", value)
            Json.obj("name" -> value)
          }
      })
      .property("status", UMapping.string)(
        _.select(_.choose(predicate = _.value(_.locked).is(P.eq(true)), onTrue = _.constant("Locked"), onFalse = _.constant("Ok")))
          .custom { (_, value, vertex, _, graph, authContext) =>
            userSrv
              .current(graph, authContext)
              .organisations(Permissions.manageUser)(db)
              .users
              .getByIds(vertex.id.toString)
              .orFail(AuthorizationError("Operation not permitted"))
              .flatMap {
                case user if value == "Ok" =>
                  userSrv.unlock(user)(graph, authContext)
                  Success(Json.obj("status" -> value))
                case user if value == "Locked" =>
                  userSrv.lock(user)(graph, authContext)
                  Success(Json.obj("status" -> value))
                case _ => Failure(InvalidFormatAttributeError("status", "UserStatus", Set("Ok", "Locked"), FString(value)))
              }
          }
      )
      .build

  lazy val observableType: List[PublicProperty[_, _]] = PublicPropertyListBuilder[ObservableType]
    .property("name", UMapping.string)(_.field.readonly)
    .property("isAttachment", UMapping.boolean)(_.field.readonly)
    .build
}
