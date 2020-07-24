package org.thp.thehive.controllers.v1

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.BadRequestError
import org.thp.scalligraph.controllers.FPathElem
import org.thp.scalligraph.models.UniMapping
import org.thp.scalligraph.query.{NoValue, PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.steps.IdMapping
import org.thp.scalligraph.steps.StepsOps._
import org.thp.thehive.models.CaseStatus
import org.thp.thehive.services.{
  AlertSrv,
  AlertSteps,
  AuditSteps,
  CaseSrv,
  CaseSteps,
  CaseTemplateSrv,
  CaseTemplateSteps,
  LogSteps,
  ObservableSrv,
  ObservableSteps,
  OrganisationSteps,
  ProfileSteps,
  TaskSrv,
  TaskSteps,
  UserSrv,
  UserSteps
}
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}

import scala.collection.JavaConverters._
import scala.util.Failure

@Singleton
class Properties @Inject() (
    alertSrv: AlertSrv,
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    userSrv: UserSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableSrv: ObservableSrv
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
              .getOrFail("Alert")
              .flatMap(alert => alertSrv.updateTagNames(alert, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.field.updatable)
      .property("tlp", UniMapping.int)(_.field.updatable)
      .property("pap", UniMapping.int)(_.field.updatable)
      .property("read", UniMapping.boolean)(_.field.updatable)
      .property("follow", UniMapping.boolean)(_.field.updatable)
      .property("read", UniMapping.boolean)(_.field.updatable)
      .property("imported", UniMapping.boolean)(_.select(_.imported).readonly)
      .property("summary", UniMapping.string.optional)(_.field.updatable)
      .property("user", UniMapping.string)(_.field.updatable)
      .property("customFields", UniMapping.identity[JsValue])(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), alertSteps) => alertSteps.customFields(name).jsonValue
        case (_, alertSteps)                                => alertSteps.customFields.nameJsonValue.fold.map(l => JsObject(l.asScala))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- alertSrv.getOrFail(vertex)(graph)
            _ <- alertSrv.setOrCreateCustomField(c, name, Some(value))(graph, authContext)
          } yield Json.obj(s"customField.$name" -> value)
        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })(NoValue(JsNull))
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
              .getOrFail("Case")
              .flatMap(`case` => caseSrv.updateTagNames(`case`, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.boolean)(_.field.updatable)
      .property("tlp", UniMapping.int)(_.field.updatable)
      .property("pap", UniMapping.int)(_.field.updatable)
      .property("status", UniMapping.enum(CaseStatus))(_.field.updatable)
      .property("summary", UniMapping.string.optional)(_.field.updatable)
      .property("assignee", UniMapping.string.optional)(_.select(_.user.login).custom { (_, login, vertex, _, graph, authContext) =>
        for {
          c    <- caseSrv.get(vertex)(graph).getOrFail("Case")
          user <- login.map(userSrv.get(_)(graph).getOrFail("User")).flip
          _ <- user match {
            case Some(u) => caseSrv.assign(c, u)(graph, authContext)
            case None    => caseSrv.unassign(c)(graph, authContext)
          }
        } yield Json.obj("owner" -> user.map(_.login))
      })
      .property("impactStatus", UniMapping.string.optional)(_.select(_.impactStatus.value).custom { (_, value, vertex, _, graph, authContext) =>
        caseSrv
          .get(vertex)(graph)
          .getOrFail("Case")
          .flatMap { c =>
            value.fold(caseSrv.unsetImpactStatus(c)(graph, authContext))(caseSrv.setImpactStatus(c, _)(graph, authContext))
          }
          .map(_ => Json.obj("impactStatus" -> value))
      })
      .property("resolutionStatus", UniMapping.string.optional)(_.select(_.resolutionStatus.value).custom {
        (_, value, vertex, _, graph, authContext) =>
          caseSrv
            .get(vertex)(graph)
            .getOrFail("Case")
            .flatMap { c =>
              value.fold(caseSrv.unsetResolutionStatus(c)(graph, authContext))(caseSrv.setResolutionStatus(c, _)(graph, authContext))
            }
            .map(_ => Json.obj("resolutionStatus" -> value))
      })
      .build

  lazy val caseTemplate: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseTemplateSteps]
      .property("name", UniMapping.string)(_.field.updatable)
      .property("displayName", UniMapping.string)(_.field.updatable)
      .property("titlePrefix", UniMapping.string.optional)(_.field.updatable)
      .property("description", UniMapping.string.optional)(_.field.updatable)
      .property("severity", UniMapping.int.optional)(_.field.updatable)
      .property("tags", UniMapping.string.set)(
        _.select(_.tags.displayName)
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseTemplateSrv
              .get(vertex)(graph)
              .getOrFail("CaseTemplate")
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
        case (FPathElem(_, FPathElem(name, _)), alertSteps) => alertSteps.customFields(name).jsonValue
        case (_, alertSteps)                                => alertSteps.customFields.nameJsonValue.fold.map(l => JsObject(l.asScala))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- caseTemplateSrv.getOrFail(vertex)(graph)
            _ <- caseTemplateSrv.setOrCreateCustomField(c, name, Some(value), None)(graph, authContext)
          } yield Json.obj(s"customField.$name" -> value)
        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })(NoValue(JsNull))
      .build

  lazy val organisation: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[OrganisationSteps]
      .property("name", UniMapping.string)(_.field.updatable)
      .property("description", UniMapping.string)(_.field.updatable)
      .build

  lazy val profile: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[ProfileSteps]
      .property("name", UniMapping.string)(_.field.updatable)
      .property("permissions", UniMapping.string.set)(_.field.updatable)
      .build

  lazy val task: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[TaskSteps]
      .property("title", UniMapping.string)(_.field.updatable)
      .property("description", UniMapping.string.optional)(_.field.updatable)
      .property("status", UniMapping.string)(_.field.updatable)
      .property("flag", UniMapping.boolean)(_.field.updatable)
      .property("startDate", UniMapping.date.optional)(_.field.updatable)
      .property("endDate", UniMapping.date.optional)(_.field.updatable)
      .property("order", UniMapping.int)(_.field.updatable)
      .property("dueDate", UniMapping.date.optional)(_.field.updatable)
      .property("assignee", UniMapping.string.optional)(_.select(_.assignee.login).custom {
        case (_, value, vertex, _, graph, authContext) =>
          taskSrv
            .get(vertex)(graph)
            .getOrFail("Task")
            .flatMap { task =>
              value.fold(taskSrv.unassign(task)(graph, authContext)) { user =>
                userSrv
                  .get(user)(graph)
                  .getOrFail("User")
                  .flatMap(taskSrv.assign(task, _)(graph, authContext))
              }
            }
            .map(_ => Json.obj("assignee" -> value))
      })
      .build

  lazy val log: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[LogSteps]
      .property("message", UniMapping.string)(_.field.updatable)
      .property("deleted", UniMapping.boolean)(_.field.updatable)
      .property("date", UniMapping.date)(_.field.readonly)
      .property("attachment", IdMapping)(_.select(_.attachments._id).readonly)
      .build

  lazy val user: List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[UserSteps]
      .property("login", UniMapping.string)(_.field.readonly)
      .property("name", UniMapping.string)(_.field.readonly)
      .property("locked", UniMapping.boolean)(_.field.readonly)
      .property("avatar", UniMapping.string.optional)(_.select(_.avatar.attachmentId.map(id => s"/api/datastore/$id")).readonly)
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
}
