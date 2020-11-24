package org.thp.thehive.controllers.v1

import java.util.Date

import javax.inject.{Inject, Named, Singleton}
import org.thp.scalligraph.controllers.{FPathElem, FPathEmpty}
import org.thp.scalligraph.models.{Database, UMapping}
import org.thp.scalligraph.query.{PublicProperties, PublicPropertyListBuilder}
import org.thp.scalligraph.traversal.Converter
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.{BadRequestError, EntityIdOrName, RichSeq}
import org.thp.thehive.dto.v1.InputCustomFieldValue
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.AuditOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.CaseTemplateOps._
import org.thp.thehive.services.CustomFieldOps._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.TagOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services.UserOps._
import org.thp.thehive.services._
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.util.Failure

@Singleton
class Properties @Inject() (
    alertSrv: AlertSrv,
    caseSrv: CaseSrv,
    taskSrv: TaskSrv,
    userSrv: UserSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableSrv: ObservableSrv,
    customFieldSrv: CustomFieldSrv,
    @Named("with-thehive-schema") db: Database
) {

  lazy val metaProperties: PublicProperties =
    PublicPropertyListBuilder
      .forType[Product](_ => true)
      .property("_createdBy", UMapping.string)(_.field.readonly)
      .property("_createdAt", UMapping.date)(_.field.readonly)
      .property("_updatedBy", UMapping.string.optional)(_.field.readonly)
      .property("_updatedAt", UMapping.date.optional)(_.field.readonly)
      .build

  lazy val alert: PublicProperties =
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
          .filter((_, cases) =>
            cases
              .tags
              .graphMap[String, String, Converter.Identity[String]](
                { v =>
                  val namespace = UMapping.string.getProperty(v, "namespace")
                  val predicate = UMapping.string.getProperty(v, "predicate")
                  val value     = UMapping.string.optional.getProperty(v, "value")
                  Tag(namespace, predicate, value, None, 0).toString
                },
                Converter.identity[String]
              )
          )
          .converter(_ => Converter.identity[String])
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
      .property("read", UMapping.boolean)(_.field.updatable)
      .property("imported", UMapping.boolean)(_.select(_.imported).readonly)
      .property("summary", UMapping.string.optional)(_.field.updatable)
      .property("user", UMapping.string)(_.field.updatable)
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(name, _)), alertSteps) => alertSteps.customFields(name).jsonValue
        case (_, alertSteps)                                => alertSteps.customFields.nameJsonValue.fold.domainMap(JsObject(_))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- alertSrv.getOrFail(vertex)(graph)
            _ <- alertSrv.setOrCreateCustomField(c, InputCustomFieldValue(name, Some(value), None))(graph, authContext)
          } yield Json.obj(s"customField.$name" -> value)
        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })
      .build

  lazy val audit: PublicProperties =
    PublicPropertyListBuilder[Audit]
      .property("operation", UMapping.string)(_.rename("action").readonly)
      .property("details", UMapping.string)(_.field.readonly)
      .property("objectType", UMapping.string.optional)(_.field.readonly)
      .property("objectId", UMapping.string.optional)(_.field.readonly)
      .property("base", UMapping.boolean)(_.rename("mainAction").readonly)
      .property("startDate", UMapping.date)(_.rename("_createdAt").readonly)
      .property("requestId", UMapping.string)(_.field.readonly)
      .property("rootId", db.idMapping)(_.select(_.context._id).readonly)
      .build

  lazy val `case`: PublicProperties =
    PublicPropertyListBuilder[Case]
      .property("title", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .property("severity", UMapping.int)(_.field.updatable)
      .property("startDate", UMapping.date)(_.field.updatable)
      .property("endDate", UMapping.date.optional)(_.field.updatable)
      .property("number", UMapping.int)(_.field.readonly)
      .property("tags", UMapping.string.set)(
        _.select(_.tags.displayName)
          .filter((_, cases) =>
            cases
              .tags
              .graphMap[String, String, Converter.Identity[String]](
                { v =>
                  val namespace = UMapping.string.getProperty(v, "namespace")
                  val predicate = UMapping.string.getProperty(v, "predicate")
                  val value     = UMapping.string.optional.getProperty(v, "value")
                  Tag(namespace, predicate, value, None, 0).toString
                },
                Converter.identity[String]
              )
          )
          .converter(_ => Converter.identity[String])
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
      .property("assignee", UMapping.string.optional)(_.select(_.user.value(_.login)).custom { (_, login, vertex, _, graph, authContext) =>
        for {
          c    <- caseSrv.get(vertex)(graph).getOrFail("Case")
          user <- login.map(u => userSrv.get(EntityIdOrName(u))(graph).getOrFail("User")).flip
          _ <- user match {
            case Some(u) => caseSrv.assign(c, u)(graph, authContext)
            case None    => caseSrv.unassign(c)(graph, authContext)
          }
        } yield Json.obj("owner" -> user.map(_.login))
      })
      .property("impactStatus", UMapping.string.optional)(_.select(_.impactStatus.value(_.value)).custom {
        (_, value, vertex, _, graph, authContext) =>
          caseSrv
            .get(vertex)(graph)
            .getOrFail("Case")
            .flatMap { c =>
              value.fold(caseSrv.unsetImpactStatus(c)(graph, authContext))(caseSrv.setImpactStatus(c, _)(graph, authContext))
            }
            .map(_ => Json.obj("impactStatus" -> value))
      })
      .property("resolutionStatus", UMapping.string.optional)(_.select(_.resolutionStatus.value(_.value)).custom {
        (_, value, vertex, _, graph, authContext) =>
          caseSrv
            .get(vertex)(graph)
            .getOrFail("Case")
            .flatMap { c =>
              value.fold(caseSrv.unsetResolutionStatus(c)(graph, authContext))(caseSrv.setResolutionStatus(c, _)(graph, authContext))
            }
            .map(_ => Json.obj("resolutionStatus" -> value))
      })
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(idOrName, _)), caseSteps) =>
          caseSteps
            .customFields(EntityIdOrName(idOrName))
            .jsonValue
        case (_, caseSteps) => caseSteps.customFields.nameJsonValue.fold.domainMap(JsObject(_))
      }
        .filter {
          case (FPathElem(_, FPathElem(idOrName, _)), caseTraversal) =>
            db
              .roTransaction(implicit graph => customFieldSrv.get(EntityIdOrName(idOrName)).value(_.`type`).getOrFail("CustomField"))
              .map {
                case CustomFieldType.boolean => caseTraversal.customFields(EntityIdOrName(idOrName)).value(_.booleanValue)
                case CustomFieldType.date    => caseTraversal.customFields(EntityIdOrName(idOrName)).value(_.dateValue)
                case CustomFieldType.float   => caseTraversal.customFields(EntityIdOrName(idOrName)).value(_.floatValue)
                case CustomFieldType.integer => caseTraversal.customFields(EntityIdOrName(idOrName)).value(_.integerValue)
                case CustomFieldType.string  => caseTraversal.customFields(EntityIdOrName(idOrName)).value(_.stringValue)
              }
              .getOrElse(caseTraversal.constant2(null))
          case (_, caseTraversal) => caseTraversal.constant2(null)
        }
        .converter {
          case FPathElem(_, FPathElem(idOrName, _)) =>
            db
              .roTransaction { implicit graph =>
                customFieldSrv.get(EntityIdOrName(idOrName)).value(_.`type`).getOrFail("CustomField")
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
          case (FPathElem(_, FPathElem(idOrName, _)), value, vertex, _, graph, authContext) =>
            for {
              c <- caseSrv.get(vertex)(graph).getOrFail("Case")
              _ <- caseSrv.setOrCreateCustomField(c, EntityIdOrName(idOrName), Some(value), None)(graph, authContext)
            } yield Json.obj(s"customField.$idOrName" -> value)
          case (FPathElem(_, FPathEmpty), values: JsObject, vertex, _, graph, authContext) =>
            for {
              c   <- caseSrv.get(vertex)(graph).getOrFail("Case")
              cfv <- values.fields.toTry { case (n, v) => customFieldSrv.getOrFail(EntityIdOrName(n))(graph).map(cf => (cf, v, None)) }
              _   <- caseSrv.updateCustomField(c, cfv)(graph, authContext)
            } yield Json.obj("customFields" -> values)
          case _ => Failure(BadRequestError("Invalid custom fields format"))
        })
      .build

  lazy val caseTemplate: PublicProperties =
    PublicPropertyListBuilder[CaseTemplate]
      .property("name", UMapping.string)(_.field.updatable)
      .property("displayName", UMapping.string)(_.field.updatable)
      .property("titlePrefix", UMapping.string.optional)(_.field.updatable)
      .property("description", UMapping.string.optional)(_.field.updatable)
      .property("severity", UMapping.int.optional)(_.field.updatable)
      .property("tags", UMapping.string.set)(
        _.select(_.tags.displayName)
          .filter((_, cases) =>
            cases
              .tags
              .graphMap[String, String, Converter.Identity[String]](
                { v =>
                  val namespace = UMapping.string.getProperty(v, "namespace")
                  val predicate = UMapping.string.getProperty(v, "predicate")
                  val value     = UMapping.string.optional.getProperty(v, "value")
                  Tag(namespace, predicate, value, None, 0).toString
                },
                Converter.identity[String]
              )
          )
          .converter(_ => Converter.identity[String])
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
        case (FPathElem(_, FPathElem(name, _)), alertSteps) => alertSteps.customFields(name).jsonValue
        case (_, alertSteps)                                => alertSteps.customFields.nameJsonValue.fold.domainMap(JsObject(_))
      }.custom {
        case (FPathElem(_, FPathElem(name, _)), value, vertex, _, graph, authContext) =>
          for {
            c <- caseTemplateSrv.getOrFail(vertex)(graph)
            _ <- caseTemplateSrv.setOrCreateCustomField(c, name, Some(value), None)(graph, authContext)
          } yield Json.obj(s"customField.$name" -> value)
        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })
      .build

  lazy val organisation: PublicProperties =
    PublicPropertyListBuilder[Organisation]
      .property("name", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .build

  lazy val profile: PublicProperties =
    PublicPropertyListBuilder[Profile]
      .property("name", UMapping.string)(_.field.updatable)
      .property("permissions", UMapping.string.set)(_.field.updatable)
      .build

  lazy val task: PublicProperties =
    PublicPropertyListBuilder[Task]
      .property("title", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string.optional)(_.field.updatable)
      .property("status", UMapping.string)(_.field.updatable)
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("startDate", UMapping.date.optional)(_.field.updatable)
      .property("endDate", UMapping.date.optional)(_.field.updatable)
      .property("order", UMapping.int)(_.field.updatable)
      .property("dueDate", UMapping.date.optional)(_.field.updatable)
      .property("assignee", UMapping.string.optional)(_.select(_.assignee.value(_.login)).custom {
        case (_, value, vertex, _, graph, authContext) =>
          taskSrv
            .get(vertex)(graph)
            .getOrFail("Task")
            .flatMap { task =>
              value.fold(taskSrv.unassign(task)(graph, authContext)) { user =>
                userSrv
                  .get(EntityIdOrName(user))(graph)
                  .getOrFail("User")
                  .flatMap(taskSrv.assign(task, _)(graph, authContext))
              }
            }
            .map(_ => Json.obj("assignee" -> value))
      })
      .build

  lazy val log: PublicProperties =
    PublicPropertyListBuilder[Log]
      .property("message", UMapping.string)(_.field.updatable)
      .property("deleted", UMapping.boolean)(_.field.updatable)
      .property("date", UMapping.date)(_.field.readonly)
      .property("attachment", UMapping.string)(_.select(_.attachments.value(_.attachmentId)).readonly)
      .build

  lazy val user: PublicProperties =
    PublicPropertyListBuilder[User]
      .property("login", UMapping.string)(_.field.readonly)
      .property("name", UMapping.string)(_.field.readonly)
      .property("locked", UMapping.boolean)(_.field.readonly)
      .property("avatar", UMapping.string.optional)(_.select(_.avatar.value(_.attachmentId).domainMap(id => s"/api/datastore/$id")).readonly)
      .build

  lazy val observable: PublicProperties =
    PublicPropertyListBuilder[Observable]
      .property("status", UMapping.string)(_.select(_.constant("Ok")).readonly)
      .property("startDate", UMapping.date)(_.select(_._createdAt).readonly)
      .property("ioc", UMapping.boolean)(_.field.updatable)
      .property("sighted", UMapping.boolean)(_.field.updatable)
      .property("ignoreSimilarity", UMapping.boolean)(_.field.updatable)
      .property("tags", UMapping.string.set)(
        _.select(_.tags.displayName)
          .filter((_, cases) =>
            cases
              .tags
              .graphMap[String, String, Converter.Identity[String]](
                { v =>
                  val namespace = UMapping.string.getProperty(v, "namespace")
                  val predicate = UMapping.string.getProperty(v, "predicate")
                  val value     = UMapping.string.optional.getProperty(v, "value")
                  Tag(namespace, predicate, value, None, 0).toString
                },
                Converter.identity[String]
              )
          )
          .converter(_ => Converter.identity[String])
          .custom { (_, value, vertex, _, graph, authContext) =>
            observableSrv
              .getOrFail(vertex)(graph)
              .flatMap(observable => observableSrv.updateTagNames(observable, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("message", UMapping.string)(_.field.updatable)
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("dataType", UMapping.string)(_.select(_.observableType.value(_.name)).readonly)
      .property("data", UMapping.string.optional)(_.select(_.data.value(_.data)).readonly)
      // TODO add attachment ?
      .build

  lazy val taxonomy: PublicProperties =
    PublicPropertyListBuilder[Taxonomy]
      .property("namespace", UMapping.string)(_.field.readonly)
      .property("description", UMapping.string)(_.field.readonly)
      .property("version", UMapping.int)(_.field.readonly)
      .build

}
