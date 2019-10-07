package org.thp.thehive.controllers.v0

import java.util.Date

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.util.Failure

import play.api.libs.json._

import gremlin.scala.{__, By, Graph, GremlinScala, Key, Vertex}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.BadRequestError
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers.{FPathElem, Output}
import org.thp.scalligraph.models.{Database, Model, UniMapping}
import org.thp.scalligraph.query.{NoValue, PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{InputCase, OutputCase}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, CaseSteps, ShareSteps, UserSrv}

object CaseConversion {
  implicit def toOutputCase(richCase: RichCase): Output[OutputCase] =
    Output[OutputCase](
      richCase
        .into[OutputCase]
        .withFieldComputed(_.customFields, rc => JsObject(rc.customFields.map(cf => cf.name -> Json.obj(cf.typeName -> cf.toJson))))
        .withFieldComputed(_.status, _.status.toString)
        .withFieldConst(_._type, "case")
        .withFieldComputed(_.id, _._id)
        .withFieldRenamed(_.number, _.caseId)
        .withFieldRenamed(_.user, _.owner)
        .withFieldRenamed(_._updatedAt, _.updatedAt)
        .withFieldRenamed(_._updatedBy, _.updatedBy)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
        .withFieldConst(_.stats, JsObject.empty)
        .withFieldComputed(_.permissions, _.userPermissions.map(_.toString))
        .transform
    )

  implicit def fromInputCase(inputCase: InputCase): Case =
    inputCase
      .into[Case]
      .withFieldComputed(_.severity, _.severity.getOrElse(2))
//      .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date)) // FIXME use startDate from InputCase when UI will be fixed
      .withFieldConst(_.startDate, new Date)
      .withFieldComputed(_.flag, _.flag.getOrElse(false))
      .withFieldComputed(_.tlp, _.tlp.getOrElse(2))
      .withFieldComputed(_.pap, _.pap.getOrElse(2))
      .withFieldConst(_.status, CaseStatus.Open)
      .withFieldConst(_.number, 0)
      .transform

  implicit def toOutputCaseWithStats(richCaseWithStats: (RichCase, JsObject)): Output[OutputCase] =
    Output[OutputCase](
      richCaseWithStats
        ._1
        .into[OutputCase]
        .withFieldComputed(_.customFields, rc => JsObject(rc.customFields.map(cf => cf.name -> Json.obj(cf.typeName -> cf.toJson))))
        .withFieldComputed(_.status, _.status.toString)
        .withFieldConst(_._type, "case")
        .withFieldComputed(_.id, _._id)
        .withFieldRenamed(_.number, _.caseId)
        .withFieldRenamed(_.user, _.owner)
        .withFieldRenamed(_._updatedAt, _.updatedAt)
        .withFieldRenamed(_._updatedBy, _.updatedBy)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .withFieldComputed(_.tags, _.tags.map(_.toString).toSet)
        .withFieldConst(_.stats, richCaseWithStats._2)
        .withFieldComputed(_.permissions, _.userPermissions.map(_.toString))
        .transform
    )

  def observableStats(shareTraversal: GremlinScala[Vertex])(implicit db: Database, graph: Graph): GremlinScala[JsObject] =
    new ShareSteps(shareTraversal)
      .observables
      .count
      .map(count => Json.obj("count" -> count.toLong))
      .raw

  def taskStats(shareTraversal: GremlinScala[Vertex])(implicit db: Database, graph: Graph): GremlinScala[JsObject] =
    new ShareSteps(shareTraversal)
      .tasks
      .active
      .groupCount(By(Key[String]("status")))
      .map { statusAgg =>
        val (total, result) = statusAgg.asScala.foldLeft(0L -> JsObject.empty) {
          case ((t, r), (k, v)) => (t + v) -> (r + (k -> JsNumber(v.toInt)))
        }
        result + ("total" -> JsNumber(total))
      }
      .raw

  def alertStats(caseTraversal: GremlinScala[Vertex]): GremlinScala[Seq[JsObject]] =
    caseTraversal
      .inTo[AlertCase]
      .group(By(Key[String]("type")), By(Key[String]("source")))
      .map { alertAgg =>
        alertAgg
          .asScala
          .flatMap {
            case (tpe, listOfSource) =>
              listOfSource.asScala.map(s => Json.obj("type" -> tpe, "source" -> s))
          }
          .toSeq
      }
  // seq({caseId, title})

  def mergeFromStats(caseTraversal: GremlinScala[Vertex]): GremlinScala[Seq[JsObject]] = caseTraversal.constant(Nil)

  def mergeIntoStats(caseTraversal: GremlinScala[Vertex]): GremlinScala[Seq[JsObject]] = caseTraversal.constant(Nil)

  def caseStatsRenderer(implicit authContext: AuthContext, db: Database, graph: Graph): GremlinScala[Vertex] => GremlinScala[JsObject] =
    (_: GremlinScala[Vertex])
      .project(
        _.apply(
          By(
            new CaseSteps(__[Vertex])
              .share
              .project(
                _.apply(By(taskStats(__[Vertex])))
                  .and(By(observableStats(__[Vertex])))
              )
              .raw
          )
        ).and(By(alertStats(__[Vertex])))
          .and(By(mergeFromStats(__[Vertex])))
          .and(By(mergeIntoStats(__[Vertex])))
      )
      .map {
        case ((tasks, observables), alerts, mergeFrom, mergeInto) =>
          Json.obj(
            "tasks"     -> tasks,
            "artifacts" -> observables,
            "alerts"    -> alerts,
            "mergeFrom" -> mergeFrom,
            "mergeInto" -> mergeInto
          )
      }

  val tagModel: Model.Vertex[Tag] = Model.vertex[Tag]

  def caseProperties(caseSrv: CaseSrv, userSrv: UserSrv): List[PublicProperty[_, _]] =
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
        case _ => Failure(BadRequestError("Invalid custom fields format"))
      })(NoValue(JsNull))
      .property("computed.handlingDurationInHours", UniMapping.long)(
        _.select(
          _.coalesce(
            _.has(Key("endDate"))
              .sack((_: Long, endDate: Long) => endDate, By(Key[Long]("endDate")))
              .sack((_: Long) - (_: Long), By(Key[Long]("startDate")))
              .sack((_: Long) / (_: Long), By(__.constant(3600000L)))
              .sack[Long](),
            _.constant(0L)
          )
        ).readonly
      )
      .build

  def fromInputCase(inputCase: InputCase, caseTemplate: Option[RichCaseTemplate]): Case =
    caseTemplate.fold(fromInputCase(inputCase)) { ct =>
      inputCase
        .into[Case]
        .withFieldComputed(_.title, ct.titlePrefix.getOrElse("") + _.title)
        .withFieldComputed(_.severity, _.severity.orElse(ct.severity).getOrElse(2))
        .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date))
        .withFieldComputed(_.flag, _.flag.getOrElse(ct.flag))
        .withFieldComputed(_.tlp, _.tlp.orElse(ct.tlp).getOrElse(2))
        .withFieldComputed(_.pap, _.pap.orElse(ct.pap).getOrElse(2))
        .withFieldConst(_.summary, ct.summary)
        .withFieldConst(_.status, CaseStatus.Open)
        .withFieldConst(_.number, 0)
        .transform
    }
}
