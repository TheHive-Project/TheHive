package org.thp.thehive.controllers.v0

import java.util.Date

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import play.api.libs.json.{JsNumber, JsObject, Json}
import gremlin.scala.{__, By, Graph, GremlinScala, Key, Vertex}
import io.scalaland.chimney.dsl._
import org.thp.scalligraph.Output
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{Database, UniMapping}
import org.thp.scalligraph.query.{PublicProperty, PublicPropertyListBuilder}
import org.thp.scalligraph.services._
import org.thp.thehive.dto.v0.{InputCase, OutputCase}
import org.thp.thehive.models._
import org.thp.thehive.services.{CaseSrv, CaseSteps, ShareSteps, UserSrv}

object CaseConversion {
  import CustomFieldConversion._

  implicit def toOutputCase(richCase: RichCase): Output[OutputCase] =
    Output[OutputCase](
      richCase
        .into[OutputCase]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.status, _.status.toString)
        .withFieldConst(_._type, "case")
        .withFieldComputed(_.id, _._id)
        .withFieldRenamed(_.number, _.caseId)
        .withFieldRenamed(_.user, _.owner)
        .withFieldRenamed(_._updatedAt, _.updatedAt)
        .withFieldRenamed(_._updatedBy, _.updatedBy)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .withFieldComputed(_.tags, _.tags.map(_.name).toSet)
        .withFieldConst(_.stats, JsObject.empty)
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

  def observableStats(shareTraversal: GremlinScala[Vertex])(implicit db: Database, graph: Graph): GremlinScala[JsObject] =
    new ShareSteps(shareTraversal)
      .observables
      .raw
      .count()
      .map(count => Json.obj("count" -> count.toLong))

  def taskStats(shareTraversal: GremlinScala[Vertex])(implicit db: Database, graph: Graph): GremlinScala[JsObject] =
    new ShareSteps(shareTraversal)
      .tasks
      .active
      .raw
      .groupCount(By(Key[String]("status")))
      .map { statusAgg =>
        val (total, result) = statusAgg.asScala.foldLeft(0L -> JsObject.empty) {
          case ((t, r), (k, v)) => (t + v) -> (r + (k -> JsNumber(v.toInt)))
        }
        result + ("total" -> JsNumber(total))
      }

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

  def mergeFromStats(caseTraversal: GremlinScala[Vertex]): GremlinScala[Seq[JsObject]] = caseTraversal.constant(Nil)
  // seq({caseId, title})

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

  implicit def toOutputCaseWithStats(richCaseWithStats: (RichCase, JsObject)): Output[OutputCase] =
    Output[OutputCase](
      richCaseWithStats
        ._1
        .into[OutputCase]
        .withFieldComputed(_.customFields, _.customFields.map(toOutputCustomField(_).toOutput).toSet)
        .withFieldComputed(_.status, _.status.toString)
        .withFieldConst(_._type, "case")
        .withFieldComputed(_.id, _._id)
        .withFieldRenamed(_.number, _.caseId)
        .withFieldRenamed(_.user, _.owner)
        .withFieldRenamed(_._updatedAt, _.updatedAt)
        .withFieldRenamed(_._updatedBy, _.updatedBy)
        .withFieldRenamed(_._createdAt, _.createdAt)
        .withFieldRenamed(_._createdBy, _.createdBy)
        .withFieldComputed(_.tags, _.tags.map(_.name).toSet)
        .withFieldConst(_.stats, richCaseWithStats._2)
        .transform
    )

  def caseProperties(caseSrv: CaseSrv, userSrv: UserSrv): List[PublicProperty[_, _]] =
    PublicPropertyListBuilder[CaseSteps]
      .property("title", UniMapping.stringMapping)(_.simple.updatable)
      .property("description", UniMapping.stringMapping)(_.simple.updatable)
      .property("severity", UniMapping.intMapping)(_.simple.updatable)
      .property("startDate", UniMapping.dateMapping)(_.simple.updatable)
      .property("endDate", UniMapping.dateMapping.optional)(_.simple.updatable)
      .property("tags", UniMapping.stringMapping.set)(
        _.derived(_.outTo[CaseTag].value("name"))
          .custom { (_, value, vertex, _, graph, authContext) =>
            caseSrv
              .get(vertex)(graph)
              .getOrFail()
              .flatMap(`case` => caseSrv.updateTags(`case`, value)(graph, authContext))
              .map(_ => Json.obj("tags" -> value))
          }
      )
      .property("flag", UniMapping.booleanMapping)(_.simple.updatable)
      .property("tlp", UniMapping.intMapping)(_.simple.updatable)
      .property("pap", UniMapping.intMapping)(_.simple.updatable)
      .property("status", UniMapping.stringMapping)(_.simple.updatable)
      .property("summary", UniMapping.stringMapping.optional)(_.simple.updatable)
      .property("owner", UniMapping.stringMapping.optional)(_.derived(_.outTo[CaseUser].value[String]("login")).custom {
        (_, login: Option[String], vertex, _, graph, authContext) =>
          for {
            case0 <- caseSrv.get(vertex)(graph).getOrFail()
            user  <- login.map(userSrv.get(_)(graph).getOrFail()).flip
            _ <- user match {
              case Some(u) => caseSrv.assign(case0, u)(graph, authContext)
              case None    => caseSrv.unassign(case0)(graph, authContext)
            }
          } yield Json.obj("owner" -> user.map(_.login))
      })
      .property("resolutionStatus", UniMapping.stringMapping)(_.derived(_.outTo[CaseResolutionStatus].value[String]("value")).custom {
        (_, value: String, vertex, _, graph, authContext) =>
          for {
            c <- caseSrv.get(vertex)(graph).getOrFail()
            _ <- caseSrv.setResolutionStatus(c, value)(graph, authContext)
          } yield Json.obj("resolutionStatus" -> value)
      })
      .property("customFieldName", UniMapping.stringMapping)(_.derived(_.outTo[CaseCustomField].value[String]("name")).readonly)
      .property("customFieldDescription", UniMapping.stringMapping)(_.derived(_.outTo[CaseCustomField].value[String]("description")).readonly)
      .property("customFieldType", UniMapping.stringMapping)(_.derived(_.outTo[CaseCustomField].value[String]("type")).readonly)
      .property("customFieldValue", UniMapping.stringMapping)(
        _.derived(
          _.outToE[CaseCustomField].value("stringValue"),
          _.outToE[CaseCustomField].value("booleanValue"),
          _.outToE[CaseCustomField].value("integerValue"),
          _.outToE[CaseCustomField].value("floatValue"),
          _.outToE[CaseCustomField].value("dateValue")
        ).readonly
      )
      .property("computed.handlingDurationInHours", UniMapping.longMapping)(
        _.derived(
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
//        .withFieldComputed(_.startDate, _.startDate.getOrElse(new Date)) // FIXME use startDate from InputCase when UI will be fixed
        .withFieldConst(_.startDate, new Date)
        .withFieldComputed(_.flag, _.flag.getOrElse(ct.flag))
        .withFieldComputed(_.tlp, _.tlp.orElse(ct.tlp).getOrElse(2))
        .withFieldComputed(_.pap, _.pap.orElse(ct.pap).getOrElse(2))
        .withFieldConst(_.summary, ct.summary)
        .withFieldConst(_.status, CaseStatus.Open)
        .withFieldConst(_.number, 0)
        .transform
    }
}
