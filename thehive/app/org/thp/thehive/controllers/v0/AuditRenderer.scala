package org.thp.thehive.controllers.v0

import org.apache.tinkerpop.gremlin.structure.Vertex
import org.thp.scalligraph.models.UMapping
import org.thp.scalligraph.traversal.TraversalOps._
import org.thp.scalligraph.traversal._
import org.thp.thehive.controllers.v0.Conversion._
import org.thp.thehive.controllers.v1.Conversion.{patternOutput, richProcedureRenderer}
import org.thp.thehive.models._
import org.thp.thehive.services.AlertOps._
import org.thp.thehive.services.AuditOps._
import org.thp.thehive.services.CaseOps._
import org.thp.thehive.services.LogOps._
import org.thp.thehive.services.ObservableOps._
import org.thp.thehive.services.PatternOps._
import org.thp.thehive.services.ProcedureOps._
import org.thp.thehive.services.TaskOps._
import org.thp.thehive.services._
import play.api.libs.json.{JsNumber, JsObject, JsString}

import java.util.{Date, List => JList, Map => JMap}

trait AuditRenderer {

  def caseToJson: Traversal.V[Case] => Traversal[JsObject, JList[JMap[String, Any]], Converter[JsObject, JList[JMap[String, Any]]]] =
    _.richCaseWithoutPerms.option.domainMap[JsObject](_.fold(JsObject.empty)(_.toJson.as[JsObject]))

  def taskToJson: Traversal.V[Task] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    _.project(
      _.by(_.richTaskWithoutActionRequired.option.domainMap(_.fold(JsObject.empty)(_.toJson.as[JsObject])))
        .by(t => caseToJson(t.`case`))
    ).domainMap {
      case (task, case0) => task + ("case" -> case0)
    }

  def alertToJson: Traversal.V[Alert] => Traversal[JsObject, JList[JMap[String, Any]], Converter[JsObject, JList[JMap[String, Any]]]] =
    _.richAlert.option.domainMap(_.fold(JsObject.empty)(_.toJson.as[JsObject]))

  def logToJson: Traversal.V[Log] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    _.project(
      _.by(_.richLog.option.domainMap(_.fold(JsObject.empty)(_.toJson.as[JsObject])))
        .by(l => taskToJson(l.task))
    ).domainMap { case (log, task) => log + ("case_task" -> task) }

  def observableToJson: Traversal.V[Observable] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    _.project(
      _.by(_.richObservable.option.domainMap(_.fold(JsObject.empty)(_.toJson.as[JsObject])))
        .by(_.coalesceMulti(o => caseToJson(o.`case`), o => alertToJson(o.alert)))
    ).domainMap {
      case (obs, caseOrAlert) => obs + ((caseOrAlert \ "_type").asOpt[String].getOrElse("<unknown>") -> caseOrAlert)
    }

  case class Job(
      workerId: String,
      workerName: String,
      workerDefinition: String,
      status: String,
      startDate: Date,
      endDate: Date,
      report: Option[JsObject],
      cortexId: String,
      cortexJobId: String
  )
  def jobToJson
      : Traversal[Vertex, Vertex, IdentityConverter[Vertex]] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    _.project(_.by.by)
      .domainMap {
        case (vertex, _) =>
          JsObject(
            UMapping.string.optional.getProperty(vertex, "workerId").map(v => "analyzerId" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "workerName").map(v => "analyzerName" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "workerDefinition").map(v => "analyzerDefinition" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "status").map(v => "status" -> JsString(v)).toList :::
              UMapping.date.optional.getProperty(vertex, "startDate").map(v => "startDate" -> JsNumber(v.getTime)).toList :::
              UMapping.date.optional.getProperty(vertex, "endDate").map(v => "endDate" -> JsNumber(v.getTime)).toList :::
              UMapping.string.optional.getProperty(vertex, "cortexId").map(v => "cortexId" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "cortexJobId").map(v => "cortexJobId" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "_createdBy").map(v => "_createdBy" -> JsString(v)).toList :::
              UMapping.date.optional.getProperty(vertex, "_createdAt").map(v => "_createdAt" -> JsNumber(v.getTime)).toList :::
              UMapping.string.optional.getProperty(vertex, "_updatedBy").map(v => "_updatedBy" -> JsString(v)).toList :::
              UMapping.date.optional.getProperty(vertex, "_updatedAt").map(v => "_updatedAt" -> JsNumber(v.getTime)).toList :::
              UMapping.string.optional.getProperty(vertex, "_type").map(v => "_type" -> JsString(v)).toList :::
              UMapping.string.optional.getProperty(vertex, "_id").map(v => "_id" -> JsString(v)).toList
          )
      }

  def patternToJson: Traversal.V[Pattern] => Traversal[JsObject, JList[JMap[String, Any]], Converter[JsObject, JList[JMap[String, Any]]]] =
    _.richPattern.option.domainMap(_.fold(JsObject.empty)(_.toJson.as[JsObject]))

  def procedureToJson: Traversal.V[Procedure] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    _.project(
      _.by(_.richProcedure.option.domainMap(_.fold(JsObject.empty)(_.toJson.as[JsObject])))
        .by(t => caseToJson(t.`case`))
        .by(t => patternToJson(t.pattern))
    )
      .domainMap {
        case (procedure, case0, pattern) => procedure + ("case" -> case0) + ("pattern" -> pattern)
      }

  def auditRenderer: Traversal.V[Audit] => Traversal[JsObject, JMap[String, Any], Converter[JsObject, JMap[String, Any]]] =
    (_: Traversal.V[Audit])
      .coalesceIdent[Vertex](_.`object`, _.identity)
      .chooseValue(
        _.on(_.label)
          .option("Case", t => caseToJson(t.v[Case]))
          .option("Task", t => taskToJson(t.v[Task]))
          .option("Log", t => logToJson(t.v[Log]))
          .option("Observable", t => observableToJson(t.v[Observable]))
          .option("Alert", t => alertToJson(t.v[Alert]))
          .option("Procedure", t => procedureToJson(t.v[Procedure]))
          .option("Job", jobToJson)
          .none(_.constant2[JsObject, JMap[String, Any]](JsObject.empty))
      )

  def jsonSummary(auditSrv: AuditSrv, requestId: String)(implicit graph: Graph): JsObject =
    auditSrv
      .startTraversal
      .has(_.requestId, requestId)
      .has(_.mainAction, false)
      .group(
        _.byValue(_.objectType),
        _.by(_.groupCount(_.byValue(_.action)))
      )
      .headOption
      .fold(JsObject.empty) { m =>
        JsObject(
          m.map {
            case (o, ac) =>
              fromObjectType(o) -> JsObject(ac.map {
                case (a, c) =>
                  actionToOperation(a) -> JsNumber(c)
              })
          }
        )
      }

}
