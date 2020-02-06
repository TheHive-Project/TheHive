package org.thp.thehive.migration.th3

import java.util.{Base64, Date}

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.elastic4play.services.Attachment
import org.thp.thehive.connector.cortex.models.{Action, Job, JobStatus}
import org.thp.thehive.migration.dto._
import org.thp.thehive.models._
import org.thp.thehive.services.{OrganisationSrv, ProfileSrv, UserSrv}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import org.thp.thehive.controllers.v0

trait Conversion {

  import org.elastic4play.services.JsonFormat.attachmentFormat

  def readAttachment(id: String): Source[ByteString, NotUsed]
  val mainOrganisation: String

  implicit val metaDataReads: Reads[MetaData] =
    ((JsPath \ "_id").read[String] and
      (JsPath \ "createdBy").readWithDefault[String]("admin") and
      (JsPath \ "createdAt").readWithDefault[Date](new Date) and
      (JsPath \ "updatedBy").readNullable[String] and
      (JsPath \ "updatedAt").readNullable[Date])(MetaData.apply _)

  implicit val caseReads: Reads[InputCase] = Reads[InputCase] { json =>
    for {
      metaData    <- json.validate[MetaData]
      number      <- (json \ "caseId").validate[Int]
      title       <- (json \ "title").validate[String]
      description <- (json \ "description").validate[String]
      severity    <- (json \ "severity").validate[Int]
      startDate   <- (json \ "startDate").validate[Date]
      endDate     <- (json \ "endDate").validateOpt[Date]
      flag        <- (json \ "flag").validate[Boolean]
      tlp         <- (json \ "tlp").validate[Int]
      pap         <- (json \ "pap").validate[Int]
      status      <- (json \ "status").validate[CaseStatus.Value]
      summary     <- (json \ "summary").validateOpt[String]
      user        <- (json \ "owner").validateOpt[String]
      tags        <- (json \ "tags").validate[Set[String]]
      metrics     <- (json \ "metrics").validate[JsObject]
      metricsValue = metrics.value.map {
        case (name, value) => name -> Some(value)
      }
      customFields <- (json \ "customFields").validate[JsObject]
      customFieldsValue = customFields.value.map {
        case (name, value) =>
          name -> Some((value \ "string") orElse (value \ "boolean") orElse (value \ "number") orElse (value \ "date") getOrElse JsNull)
      }
    } yield InputCase(
      Case(number, title, description, severity, startDate, endDate, flag, tlp, pap, status, summary),
      user,
      Map(mainOrganisation -> "all"),
      tags,
      (metricsValue ++ customFieldsValue).toMap,
      None,
      metaData
    )
  }

  implicit val observableReads: Reads[InputObservable] = Reads[InputObservable] { json =>
    for {
      metaData <- json.validate[MetaData]
      message  <- (json \ "message").validateOpt[String]
      tlp      <- (json \ "tlp").validate[Int]
      ioc      <- (json \ "ioc").validate[Boolean]
      sighted  <- (json \ "sighted").validate[Boolean]
      dataType <- (json \ "dataType").validate[String]
      tags     <- (json \ "tags").validate[Set[String]]
      dataOrAttachment <- (json \ "data")
        .validate[String]
        .map(Left.apply)
        .orElse(
          (json \ "attachment")
            .validate[Attachment]
            .map(a => Right(InputAttachment(a.name, a.size, a.contentType, a.hashes.map(_.toString), readAttachment(a.id))))
        )
    } yield InputObservable(
      metaData,
      Observable(message, tlp, ioc, sighted),
      Seq(mainOrganisation),
      dataType,
      tags,
      dataOrAttachment
    )
  }

  implicit val taskReads: Reads[InputTask] = Reads[InputTask] { json =>
    for {
      metaData    <- json.validate[MetaData]
      title       <- (json \ "title").validate[String]
      group       <- (json \ "group").validate[String]
      description <- (json \ "description").validateOpt[String]
      status      <- (json \ "status").validate[TaskStatus.Value]
      flag        <- (json \ "flag").validate[Boolean]
      startDate   <- (json \ "startDate").validateOpt[Date]
      endDate     <- (json \ "endDate").validateOpt[Date]
      order       <- (json \ "order").validate[Int]
      dueDate     <- (json \ "dueDate").validateOpt[Date]
      owner       <- (json \ "owner").validateOpt[String]
    } yield InputTask(
      metaData,
      Task(
        title,
        group,
        description,
        status: TaskStatus.Value,
        flag: Boolean,
        startDate: Option[Date],
        endDate: Option[Date],
        order: Int,
        dueDate: Option[Date]
      ),
      owner,
      Seq(mainOrganisation)
    )
  }

  implicit val logReads: Reads[InputLog] = Reads[InputLog] { json =>
    for {
      metaData <- json.validate[MetaData]
      message  <- (json \ "message").validate[String]
      date     <- (json \ "startDate").validate[Date]
      deleted = (json \ "status").asOpt[String].contains("Deleted")
      attachment = (json \ "attachment")
        .asOpt[Attachment]
        .map(a => InputAttachment(a.name, a.size, a.contentType, a.hashes.map(_.toString), readAttachment(a.id)))
    } yield InputLog(metaData, Log(message, date, deleted), attachment.toSeq)
  }

  implicit val alertReads: Reads[InputAlert] = Reads[InputAlert] { json =>
    for {
      metaData     <- json.validate[MetaData]
      tpe          <- (json \ "type").validate[String]
      source       <- (json \ "source").validate[String]
      sourceRef    <- (json \ "sourceRef").validate[String]
      externalLink <- (json \ "externalLink").validateOpt[String]
      title        <- (json \ "title").validate[String]
      description  <- (json \ "description").validate[String]
      severity     <- (json \ "severity").validate[Int]
      date         <- (json \ "date").validate[Date]
      lastSyncDate <- (json \ "lastSyncDate").validate[Date]
      tlp          <- (json \ "tlp").validate[Int]
      pap          <- (json \ "pap").validateOpt[Int] // not in TH3
      status       <- (json \ "status").validate[String]
      read = status == "Ignored" || status == "Imported"
      follow       <- (json \ "follow").validate[Boolean]
      caseId       <- (json \ "case").validateOpt[String]
      tags         <- (json \ "tags").validate[Set[String]]
      customFields <- (json \ "metrics").validateOpt[JsObject]
      customFieldsValue = customFields.getOrElse(JsObject.empty).value.map {
        case (name, value) =>
          name -> Some((value \ "string") orElse (value \ "boolean") orElse (value \ "number") orElse (value \ "date") getOrElse JsNull)
      }
      caseTemplate <- (json \ "caseTemplate").validateOpt[String]
    } yield InputAlert(
      metaData: MetaData,
      Alert(
        tpe,
        source,
        sourceRef,
        externalLink,
        title,
        description,
        severity,
        date,
        lastSyncDate,
        tlp,
        pap.getOrElse(2),
        read,
        follow
      ),
      caseId,
      mainOrganisation,
      tags,
      customFieldsValue.toMap,
      caseTemplate: Option[String]
    )
  }

  def alertObservableReads(metaData: MetaData): Reads[InputObservable] = Reads[InputObservable] { json =>
    for {
      dataType <- (json \ "dataType").validate[String]
      message  <- (json \ "message").validateOpt[String]
      tlp      <- (json \ "tlp").validateOpt[Int]
      tags     <- (json \ "tags").validate[Set[String]]
      ioc      <- (json \ "ioc").validateOpt[Boolean]
      dataOrAttachment <- (json \ "data")
        .validate[String]
        .map(Left.apply)
        .orElse(
          (json \ "attachment")
            .validate[Attachment]
            .map(a => Right(InputAttachment(a.name, a.size, a.contentType, a.hashes.map(_.toString), readAttachment(a.id))))
        )
    } yield InputObservable(
      metaData,
      Observable(message, tlp.getOrElse(2), ioc.getOrElse(false), sighted = false),
      Nil,
      dataType,
      tags,
      dataOrAttachment
    )

  }

  implicit val userReads: Reads[InputUser] = Reads[InputUser] { json =>
    for {
      metaData <- json.validate[MetaData]
      login    <- (json \ "_id").validate[String]
      name     <- (json \ "name").validate[String]
      apikey   <- (json \ "key").validateOpt[String]
      status   <- (json \ "status").validate[String]
      locked = status == "Locked"
      password <- (json \ "password").validateOpt[String]
      role     <- (json \ "roles").validate[Seq[String]]
      profile = if (role.contains("admin")) ProfileSrv.admin.name
      else if (role.contains("write")) ProfileSrv.analyst.name
      else if (role.contains("read")) ProfileSrv.readonly.name
      else ProfileSrv.readonly.name
      avatar = (json \ "avatar")
        .asOpt[String]
        .map { base64 =>
          val data = Base64.getDecoder.decode(base64)
          InputAttachment(s"$login.avatar", data.size.toLong, "image/png", Nil, Source.single(ByteString(data)))
        }
      organisation = if (profile == ProfileSrv.admin.name) OrganisationSrv.administration.name else mainOrganisation
    } yield InputUser(metaData, User(login, name, apikey, locked, password, None), Map(organisation -> profile), avatar)
  }

  implicit val customFieldReads: Reads[InputCustomField] = Reads[InputCustomField] { json =>
    for {
      //      metaData    <- json.validate[MetaData]
      valueJson <- (json \ "value").validate[String]
      value = Json.parse(valueJson)
      displayName <- (value \ "name").validate[String]
      name        <- (value \ "reference").validate[String]
      description <- (value \ "description").validate[String]
      tpe         <- (value \ "type").validate[String]
      customFieldType = tpe match {
        case "string"  => CustomFieldType.string
        case "number"  => CustomFieldType.integer
        case "integer" => CustomFieldType.integer
        case "float"   => CustomFieldType.float
        case "boolean" => CustomFieldType.boolean
        case "date"    => CustomFieldType.date
      }
      options <- (value \ "options").validate[Seq[JsValue]]
    } yield InputCustomField(
      MetaData(name, UserSrv.init.login, new Date, None, None),
      CustomField(name, displayName, description, customFieldType, mandatory = false, options)
    )
  }

  implicit val observableTypeReads: Reads[InputObservableType] = Reads[InputObservableType] { json =>
    for {
      //      metaData    <- json.validate[MetaData]
      valueJson <- (json \ "value").validate[String]
      value = Json.parse(valueJson)
      name <- value.validate[String]
    } yield InputObservableType(MetaData(name, UserSrv.init.login, new Date, None, None), ObservableType(name, name == "file"))
  }

  implicit val caseTemplateReads: Reads[InputCaseTemplate] = Reads[InputCaseTemplate] { json =>
    for {
      metaData    <- json.validate[MetaData]
      name        <- (json \ "name").validate[String]
      displayName <- (json \ "name").validate[String]
      description <- (json \ "description").validateOpt[String]
      titlePrefix <- (json \ "titlePrefix").validateOpt[String]
      severity    <- (json \ "severity").validateOpt[Int]
      flag = (json \ "flag").asOpt[Boolean].getOrElse(false)
      tlp     <- (json \ "tlp").validateOpt[Int]
      pap     <- (json \ "pap").validateOpt[Int]
      summary <- (json \ "summary").validateOpt[String]
      tags    <- (json \ "tags").validateOpt[Set[String]]
      metrics <- (json \ "metrics").validateOpt[JsObject]
      metricsValue = metrics.getOrElse(JsObject.empty).value.map {
        case (name, value) => name -> Some(value)
      }
      customFields <- (json \ "customFields").validateOpt[JsObject]
      customFieldsValue = customFields.getOrElse(JsObject.empty).value.map {
        case (name, value) =>
          name -> Some((value \ "string") orElse (value \ "boolean") orElse (value \ "number") orElse (value \ "date") getOrElse JsNull)
      }
    } yield InputCaseTemplate(
      metaData,
      CaseTemplate(
        name,
        displayName,
        titlePrefix,
        description,
        severity,
        flag,
        tlp,
        pap,
        summary
      ),
      mainOrganisation,
      tags.getOrElse(Set.empty),
      (metricsValue ++ customFieldsValue).toMap
    )
  }

  def caseTemplateTaskReads(metaData: MetaData): Reads[InputTask] = Reads[InputTask] { json =>
    for {
      title       <- (json \ "title").validate[String]
      group       <- (json \ "group").validateOpt[String]
      description <- (json \ "description").validateOpt[String]
      status      <- (json \ "status").validateOpt[TaskStatus.Value]
      flag        <- (json \ "flag").validateOpt[Boolean]
      startDate   <- (json \ "startDate").validateOpt[Date]
      endDate     <- (json \ "endDate").validateOpt[Date]
      order       <- (json \ "order").validateOpt[Int]
      dueDate     <- (json \ "dueDate").validateOpt[Date]
      owner       <- (json \ "owner").validateOpt[String]
    } yield InputTask(
      metaData,
      Task(
        title,
        group.getOrElse("default"),
        description,
        status.getOrElse(TaskStatus.Waiting),
        flag.getOrElse(false),
        startDate,
        endDate,
        order.getOrElse(1),
        dueDate
      ),
      owner,
      Seq(mainOrganisation)
    )
  }

  lazy val jobReads: Reads[InputJob] = Reads[InputJob] { json =>
    for {
      metaData         <- json.validate[MetaData]
      workerId         <- (json \ "analyzerId").validate[String]
      workerName       <- (json \ "analyzerId").validate[String]
      workerDefinition <- (json \ "analyzerId").validate[String]
      status           <- (json \ "status").validate[JobStatus.Value]
      startDate        <- (json \ "createdAt").validate[Date]
      endDate          <- (json \ "endDate").validate[Date]
      reportJson       <- (json \ "report").validateOpt[String]
      report = reportJson.flatMap { j =>
        (Json.parse(j) \ "full").asOpt[JsObject]
      }
      cortexId    <- (json \ "cortexId").validate[String]
      cortexJobId <- (json \ "cortexJobId").validate[String]
    } yield InputJob(
      metaData,
      Job(
        workerId,
        workerName,
        workerDefinition,
        status,
        startDate,
        endDate,
        report,
        cortexId,
        cortexJobId
      )
    )
  }

  def jobObservableReads(metaData: MetaData): Reads[InputObservable] = Reads[InputObservable] { json =>
    for {
      metaData <- json.validate[MetaData]
      message  <- (json \ "message").validateOpt[String] orElse (json \ "attributes" \ "message").validateOpt[String]
      tlp      <- (json \ "tlp").validate[Int] orElse (json \ "attributes" \ "tlp").validate[Int] orElse JsSuccess(2)
      ioc      <- (json \ "ioc").validate[Boolean] orElse (json \ "attributes" \ "ioc").validate[Boolean] orElse JsSuccess(false)
      sighted  <- (json \ "sighted").validate[Boolean] orElse (json \ "attributes" \ "sighted").validate[Boolean] orElse JsSuccess(false)
      dataType <- (json \ "dataType").validate[String] orElse (json \ "type").validate[String] orElse (json \ "attributes").validate[String]
      tags     <- (json \ "tags").validate[Set[String]] orElse (json \ "attributes" \ "tags").validate[Set[String]] orElse JsSuccess(Set.empty[String])
      dataOrAttachment <- ((json \ "data").validate[String] orElse (json \ "value").validate[String])
        .map(Left.apply)
        .orElse(
          (json \ "attachment")
            .validate[Attachment]
            .map(a => Right(InputAttachment(a.name, a.size, a.contentType, a.hashes.map(_.toString), readAttachment(a.id))))
        )
    } yield InputObservable(
      metaData,
      Observable(message, tlp, ioc, sighted),
      Seq(mainOrganisation),
      dataType,
      tags,
      dataOrAttachment
    )
  }

  implicit val actionReads: Reads[(String, InputAction)] = Reads[(String, InputAction)] { json =>
    for {
      metaData         <- json.validate[MetaData]
      workerId         <- (json \ "responderId").validate[String]
      workerName       <- (json \ "responderName").validateOpt[String]
      workerDefinition <- (json \ "responderDefinition").validateOpt[String]
      status           <- (json \ "status").validate[JobStatus.Value]
      objectType       <- (json \ "objectType").validate[String]
      objectId         <- (json \ "objectId").validate[String]
      parameters = JsObject.empty // not in th3
      startDate   <- (json \ "startDate").validate[Date]
      endDate     <- (json \ "endDate").validateOpt[Date]
      report      <- (json \ "report").validateOpt[String]
      cortexId    <- (json \ "cortexId").validateOpt[String]
      cortexJobId <- (json \ "cortexJobId").validateOpt[String]
      operations  <- (json \ "operations").validateOpt[String]
    } yield objectId -> InputAction(
      metaData,
      v0.Conversion.toObjectType(objectType),
      Action(
        workerId,
        workerName.getOrElse(workerId),
        workerDefinition.getOrElse(workerId),
        status,
        parameters,
        startDate,
        endDate,
        report.flatMap(Json.parse(_).asOpt[JsObject]),
        cortexId.getOrElse("unknown"),
        cortexJobId.getOrElse("unknown"),
        operations.flatMap(Json.parse(_).asOpt[Seq[JsObject]]).getOrElse(Nil)
      )
    )
  }

  implicit val auditReads: Reads[(String, InputAudit)] = Reads[(String, InputAudit)] { json =>
    for {
      metaData   <- json.validate[MetaData]
      requestId  <- (json \ "requestId").validate[String]
      operation  <- (json \ "operation").validate[String]
      mainAction <- (json \ "base").validate[Boolean]
      objectId   <- (json \ "objectId").validateOpt[String]
      objectType <- (json \ "objectType").validateOpt[String]
      details    <- (json \ "details").validateOpt[JsObject]
      rootId     <- (json \ "rootId").validate[String]
    } yield (
      rootId,
      InputAudit(
        metaData,
        Audit(
          requestId,
          operation match {
            case "Update"   => "update"
            case "Creation" => "create"
            case "Delete"   => "delete"
          },
          mainAction,
          objectId,
          objectType,
          details.map(_.toString)
        )
      )
    )
  }
}
