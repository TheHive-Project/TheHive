package org.thp.thehive.controllers.v1

import org.apache.tinkerpop.gremlin.structure.T
import org.thp.scalligraph.controllers.{FPathElem, FString}
import org.thp.scalligraph.models.{Database, IndexType, UMapping}
import org.thp.scalligraph.query.{PublicProperties, PublicPropertyListBuilder}
import org.thp.scalligraph.{BadRequestError, EntityId, EntityIdOrName, InvalidFormatAttributeError}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.libs.json.{JsValue, Json}

import scala.util.{Failure, Success, Try}

class Properties(
    alertSrv: AlertSrv,
    caseSrv: CaseSrv,
    shareSrv: ShareSrv,
    taskSrv: TaskSrv,
    userSrv: UserSrv,
    dashboardSrv: DashboardSrv,
    caseTemplateSrv: CaseTemplateSrv,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    override val customFieldSrv: CustomFieldSrv,
    override val organisationSrv: OrganisationSrv,
    override val customFieldValueSrv: CustomFieldValueSrv,
    db: Database
) extends TheHiveOps {

  lazy val metaProperties: PublicProperties =
    PublicPropertyListBuilder
      .metadata
      .property("_id", UMapping.entityId)(
        _.select(_._id)
          .filter[EntityId](IndexType.standard) {
            case (_, t, _, Right(p))   => t.has(T.id, p.mapValue(_.value))
            case (_, t, _, Left(true)) => t
            case (_, t, _, _)          => t.empty
          }
          .readonly
      )
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
      .property("tags", UMapping.string.sequence)(
        _.field
          .custom { (_, value, vertex, graph, authContext) =>
            alertSrv
              .get(vertex)(graph)
              .getOrFail("Alert")
              .flatMap(alert => alertSrv.updateTags(alert, value.toSet)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("pap", UMapping.int)(_.field.updatable)
      .property("read", UMapping.boolean)(_.field.updatable)
      .property("follow", UMapping.boolean)(_.field.updatable)
      .property("read", UMapping.boolean)(_.field.updatable)
      .property("imported", UMapping.boolean)(
        _.select(_.imported)
          .filter[Boolean](IndexType.standard)((_, alertTraversal, _, predicate) =>
            predicate.fold(
              b => if (b) alertTraversal else alertTraversal.empty,
              p =>
                if (p.getValue) alertTraversal.nonEmptyId(_.caseId)
                else alertTraversal.isEmptyId(_.caseId)
            )
          )
          .readonly
      )
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(idOrName, _)), alertSteps) =>
          alertSteps.customFieldJsonValue(EntityIdOrName(idOrName))
        case (_, alertSteps) => alertSteps.richCustomFields.fold.domainMap(_.toJson)
      }
        .filter[JsValue](IndexType.standard) {
          case (FPathElem(_, FPathElem(name, _)), alertTraversal, _, predicate) =>
            predicate match {
              case Right(p) =>
                val elementIds = customFieldSrv
                  .getByName(name)(alertTraversal.graph)
                  .value(_.`type`)
                  .headOption
                  .fold[Seq[EntityId]](Nil)(_.filter(customFieldValueSrv.startTraversal(alertTraversal.graph), p).value(_.elementId).toSeq)
                alertTraversal.hasId(elementIds: _*)
              case Left(true)  => alertTraversal.hasCustomField(EntityIdOrName(name))
              case Left(false) => alertTraversal.hasNotCustomField(EntityIdOrName(name))
            }
          case (_, caseTraversal, _, _) => caseTraversal.empty
        }
        .custom {
          case (FPathElem(_, FPathElem(idOrName, _)), jsonValue, vertex, graph, authContext) =>
            EntityIdOrName(idOrName).fold(
              valueId => // update the value
                customFieldValueSrv
                  .getByIds(EntityId(valueId))(graph)
                  .filter(_.alert.hasId(EntityId(vertex.id())))
                  .getOrFail("CustomField")
                  .flatMap(cfv => customFieldValueSrv.updateValue(cfv, jsonValue, None)(graph))
                  .map(cfv => Json.obj(s"customField.${cfv.name}" -> jsonValue)),
              name => // add new custom field
                for {
                  a              <- alertSrv.getOrFail(vertex)(graph)
                  cf             <- customFieldSrv.getByName(name)(graph).getOrFail("CustomField")
                  (value, order) <- cf.`type`.parseValue(jsonValue)
                  _              <- alertSrv.createCustomField(a, cf, value, order)(graph, authContext)
                } yield Json.obj(s"customField.$name" -> jsonValue)
            )
          case _ => Failure(BadRequestError("Invalid custom fields format"))
        })
      .property("case", UMapping.entityId)(_.rename("caseId").readonly)
      .property("importDate", UMapping.date.optional)(_.select(_.importDate).readonly)
      .property("computed.handlingDuration", UMapping.long)(_.select(_.handlingDuration).readonly)
      .property("computed.handlingDurationInSeconds", UMapping.long)(_.select(_.handlingDuration.math("_ / 1000").domainMap(_.toLong)).readonly)
      .property("computed.handlingDurationInMinutes", UMapping.long)(_.select(_.handlingDuration.math("_ / 60000").domainMap(_.toLong)).readonly)
      .property("computed.handlingDurationInHours", UMapping.long)(_.select(_.handlingDuration.math("_ / 3600000").domainMap(_.toLong)).readonly)
      .property("computed.handlingDurationInDays", UMapping.long)(_.select(_.handlingDuration.math("_ / 86400000").domainMap(_.toLong)).readonly)
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
      .property("tags", UMapping.string.sequence)(
        _.field.custom { (_, value, vertex, graph, authContext) =>
          caseSrv
            .get(vertex)(graph)
            .getOrFail("Case")
            .flatMap(`case` => caseSrv.updateTags(`case`, value.toSet)(graph, authContext))
            .map(_ => Json.obj("tags" -> value))
        }
      )
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("pap", UMapping.int)(_.field.updatable)
      .property("status", UMapping.enum[CaseStatus.type])(_.field.updatable)
      .property("summary", UMapping.string.optional)(_.field.updatable)
      .property("actionRequired", UMapping.boolean)(_.authSelect((t, auth) => t.isActionRequired(auth)).readonly)
      .property("assignee", UMapping.string.optional)(_.field.custom { (_, login, vertex, graph, authContext) =>
        for {
          c    <- caseSrv.get(vertex)(graph).getOrFail("Case")
          user <- login.map(u => userSrv.get(EntityIdOrName(u))(graph).getOrFail("User")).flip
          _ <- user match {
            case Some(u) => caseSrv.assign(c, u)(graph, authContext)
            case None    => caseSrv.unassign(c)(graph, authContext)
          }
        } yield Json.obj("owner" -> user.map(_.login))
      })
      .property("impactStatus", UMapping.string.optional)(_.field.custom { (_, value, vertex, graph, authContext) =>
        caseSrv
          .get(vertex)(graph)
          .getOrFail("Case")
          .flatMap { c =>
            value.fold(caseSrv.unsetImpactStatus(c)(graph, authContext))(caseSrv.setImpactStatus(c, _)(graph, authContext))
          }
          .map(_ => Json.obj("impactStatus" -> value))
      })
      .property("resolutionStatus", UMapping.string.optional)(_.field.custom { (_, value, vertex, graph, authContext) =>
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
          caseSteps.customFieldJsonValue(EntityIdOrName(idOrName))
        case (_, caseSteps) => caseSteps.richCustomFields.fold.domainMap(_.toJson)
      }
        .filter[JsValue](IndexType.standard) {
          case (FPathElem(_, FPathElem(name, _)), caseTraversal, _, predicate) =>
            predicate match {
              case Right(p) =>
                val elementIds = customFieldSrv
                  .getByName(name)(caseTraversal.graph)
                  .value(_.`type`)
                  .headOption
                  .fold[Seq[EntityId]](Nil)(_.filter(customFieldValueSrv.startTraversal(caseTraversal.graph), p).value(_.elementId).toSeq)
                caseTraversal.hasId(elementIds: _*)
              case Left(true)  => caseTraversal.hasCustomField(EntityIdOrName(name))
              case Left(false) => caseTraversal.hasNotCustomField(EntityIdOrName(name))
            }
          case (_, caseTraversal, _, _) => caseTraversal.empty
        }
        .custom {
          case (FPathElem(_, FPathElem(idOrName, _)), jsonValue, vertex, graph, authContext) =>
            EntityIdOrName(idOrName).fold(
              valueId => // update the value
                customFieldValueSrv
                  .getByIds(EntityId(valueId))(graph)
                  .filter(_.`case`.hasId(EntityId(vertex.id())))
                  .getOrFail("CustomField")
                  .flatMap(cfv => customFieldValueSrv.updateValue(cfv, jsonValue, None)(graph))
                  .map(cfv => Json.obj(s"customField.${cfv.name}" -> jsonValue)),
              name => // add new custom field
                for {
                  c              <- caseSrv.getOrFail(vertex)(graph)
                  cf             <- customFieldSrv.getByName(name)(graph).getOrFail("CustomField")
                  (value, order) <- cf.`type`.parseValue(jsonValue)
                  _              <- caseSrv.createCustomField(c, cf, value, order)(graph, authContext)
                } yield Json.obj(s"customField.$name" -> jsonValue)
            )
          case _ => Failure(BadRequestError("Invalid custom fields format"))
        })
      .property("computed.handlingDuration", UMapping.long)(_.select(_.handlingDuration).readonly)
      .property("computed.handlingDurationInSeconds", UMapping.long)(_.select(_.handlingDuration.math("_ / 1000").domainMap(_.toLong)).readonly)
      .property("computed.handlingDurationInMinutes", UMapping.long)(_.select(_.handlingDuration.math("_ / 60000").domainMap(_.toLong)).readonly)
      .property("computed.handlingDurationInHours", UMapping.long)(_.select(_.handlingDuration.math("_ / 3600000").domainMap(_.toLong)).readonly)
      .property("computed.handlingDurationInDays", UMapping.long)(_.select(_.handlingDuration.math("_ / 86400000").domainMap(_.toLong)).readonly)
      .property("viewingOrganisation", UMapping.string)(
        _.select(t => t.value(_.organisationIds).domainMap(organisationSrv.getName(_)(t.graph)))
          .filter[String](IndexType.standard) {
            case (_, caseTraversal, _, Right(orgNamePredicate)) =>
              val organisationId = orgNamePredicate.mapValue(o => organisationSrv.getId(EntityIdOrName(o))(caseTraversal.graph))
              caseTraversal.has(_.organisationIds, organisationId)
            case (_, caseTraversal, _, Left(true)) =>
              caseTraversal
            case (_, caseTraversal, _, Left(false)) =>
              caseTraversal.empty
          }
          .readonly
      )
      .property("owningOrganisation", UMapping.string)(
        _.select(t => t.value(_.owningOrganisation).domainMap(organisationSrv.getName(_)(t.graph)))
          .filter[String](IndexType.standard) {
            case (_, caseTraversal, _, Right(orgNamePredicate)) =>
              val organisationId = orgNamePredicate.mapValue(o => organisationSrv.getId(EntityIdOrName(o))(caseTraversal.graph))
              caseTraversal.has(_.owningOrganisation, organisationId)
            case (_, caseTraversal, _, Left(true)) =>
              caseTraversal
            case (_, caseTraversal, _, Left(false)) =>
              caseTraversal.empty
          }
          .readonly
      )
      .property("patternId", UMapping.string.sequence)(_.select(_.procedure.pattern.value(_.patternId)).readonly)
      .property("taskRule", UMapping.string)(
        _.authSelect(_.share(_).value(_.taskRule)).custom((_, value, vertex, graph, authContext) =>
          for {
            c <- caseSrv.getOrFail(vertex)(graph)
            _ <- shareSrv.updateTaskRule(c, value)(graph, authContext)
          } yield Json.obj("taskRule" -> value)
        )
      )
      .property("observableRule", UMapping.string)(
        _.authSelect(_.share(_).value(_.observableRule)).custom((_, value, vertex, graph, authContext) =>
          for {
            c <- caseSrv.getOrFail(vertex)(graph)
            _ <- shareSrv.updateObservableRule(c, value)(graph, authContext)
          } yield Json.obj("observableRule" -> value)
        )
      )
      .build

  lazy val caseTemplate: PublicProperties =
    PublicPropertyListBuilder[CaseTemplate]
      .property("name", UMapping.string)(_.field.updatable)
      .property("displayName", UMapping.string)(_.field.updatable)
      .property("titlePrefix", UMapping.string.optional)(_.field.updatable)
      .property("description", UMapping.string.optional)(_.field.updatable)
      .property("severity", UMapping.int.optional)(_.field.updatable)
      .property("tags", UMapping.string.sequence)(
        _.field
          .custom { (_, value, vertex, graph, authContext) =>
            caseTemplateSrv
              .get(vertex)(graph)
              .getOrFail("CaseTemplate")
              .flatMap(caseTemplate => caseTemplateSrv.updateTags(caseTemplate, value.toSet)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UMapping.boolean)(_.field.updatable)
      .property("tlp", UMapping.int.optional)(_.field.updatable)
      .property("pap", UMapping.int.optional)(_.field.updatable)
      .property("summary", UMapping.string.optional)(_.field.updatable)
      .property("customFields", UMapping.jsonNative)(_.subSelect {
        case (FPathElem(_, FPathElem(idOrName, _)), caseTemplateSteps) =>
          caseTemplateSteps.customFieldJsonValue(EntityIdOrName(idOrName))
        case (_, caseTemplateSteps) => caseTemplateSteps.richCustomFields.fold.domainMap(_.toJson)
      }
        .filter[JsValue](IndexType.standard) {
          case (FPathElem(_, FPathElem(name, _)), caseTemplateTraversal, _, predicate) =>
            predicate match {
              case Right(p) =>
                val elementIds = customFieldSrv
                  .getByName(name)(caseTemplateTraversal.graph)
                  .value(_.`type`)
                  .headOption
                  .fold[Seq[EntityId]](Nil)(_.filter(customFieldValueSrv.startTraversal(caseTemplateTraversal.graph), p).value(_.elementId).toSeq)
                caseTemplateTraversal.hasId(elementIds: _*)
              case Left(true)  => caseTemplateTraversal.hasCustomField(EntityIdOrName(name))
              case Left(false) => caseTemplateTraversal.hasNotCustomField(EntityIdOrName(name))
            }
          case (_, caseTraversal, _, _) => caseTraversal.empty
        }
        .custom {
          case (FPathElem(_, FPathElem(idOrName, _)), jsonValue, vertex, graph, authContext) =>
            EntityIdOrName(idOrName).fold(
              valueId => // update the value
                customFieldValueSrv
                  .getByIds(EntityId(valueId))(graph)
                  .filter(_.`case`.hasId(EntityId(vertex.id())))
                  .getOrFail("CustomField")
                  .flatMap(cfv => customFieldValueSrv.updateValue(cfv, jsonValue, None)(graph))
                  .map(cfv => Json.obj(s"customField.${cfv.name}" -> jsonValue)),
              name => // add new custom field
                for {
                  ct             <- caseTemplateSrv.getOrFail(vertex)(graph)
                  cf             <- customFieldSrv.getByName(name)(graph).getOrFail("CustomField")
                  (value, order) <- cf.`type`.parseValue(jsonValue)
                  _              <- caseTemplateSrv.createCustomField(ct, cf, value, order)(graph, authContext)
                } yield Json.obj(s"customField.$name" -> jsonValue)
            )
          case _ => Failure(BadRequestError("Invalid custom fields format"))
        })
      .build

  lazy val organisation: PublicProperties =
    PublicPropertyListBuilder[Organisation]
      .property("name", UMapping.string)(_.field.updatable)
      .property("description", UMapping.string)(_.field.updatable)
      .property("taskRule", UMapping.string)(_.field.updatable)
      .property("observableRule", UMapping.string)(_.field.updatable)
      .build

  lazy val pattern: PublicProperties =
    PublicPropertyListBuilder[Pattern]
      .property("patternId", UMapping.string)(_.field.readonly)
      .property("name", UMapping.string)(_.field.readonly)
      .property("description", UMapping.string.optional)(_.field.updatable)
      .property("tactics", UMapping.string.set)(_.field.readonly)
      .property("url", UMapping.string)(_.field.updatable)
      .property("patternType", UMapping.string)(_.field.readonly)
      .property("capecId", UMapping.string.optional)(_.field.readonly)
      .property("capecUrl", UMapping.string.optional)(_.field.readonly)
      .property("revoked", UMapping.boolean)(_.field.readonly)
      .property("dataSources", UMapping.string.sequence)(_.field.readonly)
      .property("defenseBypassed", UMapping.string.sequence)(_.field.readonly)
      .property("detection", UMapping.string.optional)(_.field.readonly)
      .property("permissionsRequired", UMapping.string.sequence)(_.field.readonly)
      .property("platforms", UMapping.string.sequence)(_.field.readonly)
      .property("remoteSupport", UMapping.boolean)(_.field.readonly)
      .property("systemRequirements", UMapping.string.sequence)(_.field.readonly)
      .property("version", UMapping.string.optional)(_.rename("revision").readonly)
      .property("parent", UMapping.string.optional)(_.select(_.parent.value(_.patternId)).readonly)
      .build

  lazy val procedure: PublicProperties =
    PublicPropertyListBuilder[Procedure]
      .property("description", UMapping.string)(_.field.updatable)
      .property("occurDate", UMapping.date)(_.field.updatable)
      .property("tactic", UMapping.string)(_.field.updatable)
      .property("patternId", UMapping.string)(_.select(_.pattern.value(_.patternId)).readonly)
      .build

  lazy val profile: PublicProperties =
    PublicPropertyListBuilder[Profile]
      .property("name", UMapping.string)(_.field.updatable)
      .property("permissions", UMapping.string.set)(_.field.updatable)
      .build

  lazy val share: PublicProperties =
    PublicPropertyListBuilder[Share]
      .property("caseId", UMapping.entityId)(_.select(_.`case`._id).readonly)
      .property("caseNumber", UMapping.int)(_.select(_.`case`.value(_.number)).readonly)
      .property("organisationId", UMapping.entityId)(_.select(_.organisation._id).readonly)
      .property("organisationName", UMapping.string)(_.select(_.organisation.value(_.name)).readonly)
      .property("profileId", UMapping.entityId)(_.select(_.profile._id).readonly)
      .property("profileName", UMapping.string)(_.select(_.profile.value(_.name)).readonly)
      .property("owner", UMapping.boolean)(_.field.readonly)
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
      .property("group", UMapping.string)(_.field.updatable)
      .property("assignee", UMapping.string.optional)(_.field.custom {
        case (_, value, vertex, graph, authContext) =>
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
      .property("actionRequired", UMapping.boolean)(_.authSelect { (t, authContext) =>
        t.actionRequired(authContext)
      }.readonly)
      .build

  lazy val log: PublicProperties =
    PublicPropertyListBuilder[Log]
      .property("message", UMapping.string)(_.field.updatable)
      .property("date", UMapping.date)(_.field.readonly)
      .property("attachment.name", UMapping.string.optional)(_.select(_.attachments.value(_.name)).readonly)
      .property("attachment.hashes", UMapping.hash.sequence)(_.select(_.attachments.value(_.hashes)).readonly)
      .property("attachment.size", UMapping.long.optional)(_.select(_.attachments.value(_.size)).readonly)
      .property("attachment.contentType", UMapping.string.optional)(_.select(_.attachments.value(_.contentType)).readonly)
      .property("attachment.id", UMapping.string.optional)(_.select(_.attachments.value(_.attachmentId)).readonly)
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
      .property("startDate", UMapping.date)(_.rename("_createdAt").readonly)
      .property("ioc", UMapping.boolean)(_.field.updatable)
      .property("sighted", UMapping.boolean)(_.field.updatable)
      .property("ignoreSimilarity", UMapping.boolean)(_.field.updatable)
      .property("tags", UMapping.string.sequence)(
        _.field.custom { (_, value, vertex, graph, authContext) =>
          observableSrv
            .getOrFail(vertex)(graph)
            .flatMap(observable => observableSrv.updateTags(observable, value.toSet)(graph, authContext))
            .map(_ => Json.obj("tags" -> value))
        }
      )
      .property("message", UMapping.string)(_.field.updatable)
      .property("tlp", UMapping.int)(_.field.updatable)
      .property("dataType", UMapping.string)(_.field.custom { (_, value, vertex, graph, _) =>
        val observable = observableSrv.model.converter(vertex)
        for {
          currentDataType <- observableTypeSrv.getByName(observable.dataType)(graph).getOrFail("ObservableType")
          newDataType     <- observableTypeSrv.getByName(value)(graph).getOrFail("ObservableType")
          isSameType = currentDataType.isAttachment == newDataType.isAttachment
          _ <- if (isSameType) Success(()) else Failure(BadRequestError("Can not update dataType: isAttachment does not match"))
          _ <- Try(observableSrv.get(vertex)(graph).update(_.dataType, value).iterate())
        } yield Json.obj("dataType" -> value)
      })
      .property("data", UMapping.string.optional)(_.field.readonly)
      .property("attachment.name", UMapping.string.optional)(_.select(_.attachments.value(_.name)).readonly)
      .property("attachment.hashes", UMapping.hash.sequence)(_.select(_.attachments.value(_.hashes)).readonly)
      .property("attachment.size", UMapping.long.optional)(_.select(_.attachments.value(_.size)).readonly)
      .property("attachment.contentType", UMapping.string.optional)(_.select(_.attachments.value(_.contentType)).readonly)
      .property("attachment.id", UMapping.string.optional)(_.rename("attachmentId").readonly)
      .build

  lazy val taxonomy: PublicProperties =
    PublicPropertyListBuilder[Taxonomy]
      .property("namespace", UMapping.string)(_.field.readonly)
      .property("description", UMapping.string)(_.field.readonly)
      .property("version", UMapping.int)(_.field.readonly)
      .property("enabled", UMapping.boolean)(_.select(_.enabled).readonly)
      .build

  lazy val tag: PublicProperties =
    PublicPropertyListBuilder[Tag]
      .property("namespace", UMapping.string)(_.field.readonly)
      .property("predicate", UMapping.string)(_.field.updatable)
      .property("value", UMapping.string.optional)(_.field.readonly)
      .property("description", UMapping.string.optional)(_.field.updatable)
      .property("colour", UMapping.string)(_.field.updatable)
      .property("text", UMapping.string)(
        _.select(_.displayName)
          .filter[String](IndexType.standard) {
            case (_, tags, authContext, Right(predicate)) => tags.freetags(organisationSrv)(authContext).has(_.predicate, predicate)
            case (_, tags, _, Left(true))                 => tags
            case (_, tags, _, Left(false))                => tags.empty
          }
          .readonly
      )
      .build

  lazy val dashboard: PublicProperties = PublicPropertyListBuilder[Dashboard]
    .property("title", UMapping.string)(_.field.updatable)
    .property("description", UMapping.string)(_.field.updatable)
    .property("definition", UMapping.string)(_.field.updatable)
    .property("status", UMapping.string)(
      _.select(_.choose(_.organisation, "Shared", "Private"))
        .custom {
          case (_, "Shared", vertex, graph, authContext) =>
            for {
              dashboard <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
              _         <- dashboardSrv.share(dashboard, authContext.organisation, writable = true)(graph, authContext)
            } yield Json.obj("status" -> "Shared")

          case (_, "Private", vertex, graph, authContext) =>
            for {
              d <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
              _ <- dashboardSrv.unshare(d, authContext.organisation)(graph, authContext)
            } yield Json.obj("status" -> "Private")

          case (_, "Deleted", vertex, graph, authContext) =>
            for {
              d <- dashboardSrv.get(vertex)(graph).filter(_.user.current(authContext)).getOrFail("Dashboard")
              _ <- dashboardSrv.remove(d)(graph, authContext)
            } yield Json.obj("status" -> "Deleted")

          case (_, status, _, _, _) =>
            Failure(InvalidFormatAttributeError("status", "String", Set("Shared", "Private", "Deleted"), FString(status)))
        }
    )
    .build

}
