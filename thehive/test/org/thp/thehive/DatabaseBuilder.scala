package org.thp.thehive

import java.io.File

import scala.io.Source
import scala.reflect.runtime.{universe => ru}
import scala.util.{Failure, Success, Try}

import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import gremlin.scala.{KeyValue => _, _}
import javax.inject.{Inject, Singleton}
import org.scalactic.Or
import org.thp.scalligraph.RichOption
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, Entity, Schema}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.thehive.models._
import org.thp.thehive.services._

@Singleton
class DatabaseBuilder @Inject() (
    schema: Schema,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    profileSrv: ProfileSrv,
    caseSrv: CaseSrv,
    customFieldSrv: CustomFieldSrv,
    caseTemplateSrv: CaseTemplateSrv,
    impactStatusSrv: ImpactStatusSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    shareSrv: ShareSrv,
    roleSrv: RoleSrv,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    taskSrv: TaskSrv,
    tagSrv: TagSrv,
    keyValueSrv: KeyValueSrv,
    dataSrv: DataSrv,
    logSrv: LogSrv,
    alertSrv: AlertSrv,
    attachmentSrv: AttachmentSrv,
    dashboardSrv: DashboardSrv,
    pageSrv: PageSrv
) {

  lazy val logger: Logger = Logger(getClass)

  def build()(implicit db: Database, authContext: AuthContext): Try[Unit] = {

    lazy val logger: Logger = Logger(getClass)
    logger.info("Initialize database schema")
    db.createSchemaFrom(schema)
    db.tryTransaction { implicit graph =>
      val idMap =
        createVertex(caseSrv, FieldsParser[Case]) ++
          createVertex(userSrv, FieldsParser[User]) ++
          createVertex(customFieldSrv, FieldsParser[CustomField]) ++
          createVertex(organisationSrv, FieldsParser[Organisation]) ++
          createVertex(caseTemplateSrv, FieldsParser[CaseTemplate]) ++
          createVertex(shareSrv, FieldsParser[Share]) ++
          createVertex(roleSrv, FieldsParser[Role]) ++
          createVertex(profileSrv, FieldsParser[Profile]) ++
          createVertex(observableSrv, FieldsParser[Observable]) ++
          createVertex(observableTypeSrv, FieldsParser[ObservableType]) ++
          createVertex(taskSrv, FieldsParser[Task]) ++
          createVertex(keyValueSrv, FieldsParser[KeyValue]) ++
          createVertex(dataSrv, FieldsParser[Data]) ++
          createVertex(logSrv, FieldsParser[Log]) ++
          createVertex(alertSrv, FieldsParser[Alert]) ++
          createVertex(resolutionStatusSrv, FieldsParser[ResolutionStatus]) ++
          createVertex(impactStatusSrv, FieldsParser[ImpactStatus]) ++
          createVertex(attachmentSrv, FieldsParser[Attachment]) ++
          createVertex(tagSrv, FieldsParser[Tag]) ++
          createVertex(pageSrv, FieldsParser[Page]) ++
          createVertex(dashboardSrv, FieldsParser[Dashboard])

      createEdge(organisationSrv.organisationOrganisationSrv, organisationSrv, organisationSrv, FieldsParser[OrganisationOrganisation], idMap)
      createEdge(organisationSrv.organisationShareSrv, organisationSrv, shareSrv, FieldsParser[OrganisationShare], idMap)

      createEdge(roleSrv.userRoleSrv, userSrv, roleSrv, FieldsParser[UserRole], idMap)

      createEdge(shareSrv.shareProfileSrv, shareSrv, profileSrv, FieldsParser[ShareProfile], idMap)
      createEdge(shareSrv.shareObservableSrv, shareSrv, observableSrv, FieldsParser[ShareObservable], idMap)
      createEdge(shareSrv.shareTaskSrv, shareSrv, taskSrv, FieldsParser[ShareTask], idMap)
      createEdge(shareSrv.shareCaseSrv, shareSrv, caseSrv, FieldsParser[ShareCase], idMap)

      createEdge(roleSrv.roleOrganisationSrv, roleSrv, organisationSrv, FieldsParser[RoleOrganisation], idMap)
      createEdge(roleSrv.roleProfileSrv, roleSrv, profileSrv, FieldsParser[RoleProfile], idMap)

      createEdge(observableSrv.observableKeyValueSrv, observableSrv, keyValueSrv, FieldsParser[ObservableKeyValue], idMap)
      createEdge(observableSrv.observableObservableType, observableSrv, observableTypeSrv, FieldsParser[ObservableObservableType], idMap)
      createEdge(observableSrv.observableDataSrv, observableSrv, dataSrv, FieldsParser[ObservableData], idMap)
      createEdge(observableSrv.observableAttachmentSrv, observableSrv, attachmentSrv, FieldsParser[ObservableAttachment], idMap)
      createEdge(observableSrv.observableTagSrv, observableSrv, tagSrv, FieldsParser[ObservableTag], idMap)

      createEdge(taskSrv.taskUserSrv, taskSrv, userSrv, FieldsParser[TaskUser], idMap)
      createEdge(taskSrv.taskLogSrv, taskSrv, logSrv, FieldsParser[TaskLog], idMap)

      createEdge(caseSrv.caseUserSrv, caseSrv, userSrv, FieldsParser[CaseUser], idMap)
      createEdge(caseSrv.mergedFromSrv, caseSrv, caseSrv, FieldsParser[MergedFrom], idMap)
      createEdge(caseSrv.caseCaseTemplateSrv, caseSrv, caseTemplateSrv, FieldsParser[CaseCaseTemplate], idMap)
      createEdge(caseSrv.caseResolutionStatusSrv, caseSrv, resolutionStatusSrv, FieldsParser[CaseResolutionStatus], idMap)
      createEdge(caseSrv.caseImpactStatusSrv, caseSrv, impactStatusSrv, FieldsParser[CaseImpactStatus], idMap)
      createEdge(caseSrv.caseCustomFieldSrv, caseSrv, customFieldSrv, FieldsParser[CaseCustomField], idMap)
      createEdge(caseSrv.caseTagSrv, caseSrv, tagSrv, FieldsParser[CaseTag], idMap)

      createEdge(caseTemplateSrv.caseTemplateOrganisationSrv, caseTemplateSrv, organisationSrv, FieldsParser[CaseTemplateOrganisation], idMap)
      createEdge(caseTemplateSrv.caseTemplateTaskSrv, caseTemplateSrv, taskSrv, FieldsParser[CaseTemplateTask], idMap)
      createEdge(caseTemplateSrv.caseTemplateCustomFieldSrv, caseTemplateSrv, customFieldSrv, FieldsParser[CaseTemplateCustomField], idMap)
      createEdge(caseTemplateSrv.caseTemplateTagSrv, caseTemplateSrv, tagSrv, FieldsParser[CaseTemplateTag], idMap)

      createEdge(alertSrv.alertOrganisationSrv, alertSrv, organisationSrv, FieldsParser[AlertOrganisation], idMap)
      createEdge(alertSrv.alertObservableSrv, alertSrv, observableSrv, FieldsParser[AlertObservable], idMap)
      createEdge(alertSrv.alertCaseSrv, alertSrv, caseSrv, FieldsParser[AlertCase], idMap)
      createEdge(alertSrv.alertCaseTemplateSrv, alertSrv, caseTemplateSrv, FieldsParser[AlertCaseTemplate], idMap)
      createEdge(alertSrv.alertCustomFieldSrv, alertSrv, customFieldSrv, FieldsParser[AlertCustomField], idMap)
      createEdge(alertSrv.alertTagSrv, alertSrv, tagSrv, FieldsParser[AlertTag], idMap)

      createEdge(pageSrv.organisationPageSrv, organisationSrv, pageSrv, FieldsParser[OrganisationPage], idMap)

      createEdge(dashboardSrv.dashboardUserSrv, dashboardSrv, userSrv, FieldsParser[DashboardUser], idMap)
      createEdge(dashboardSrv.organisationDashboardSrv, organisationSrv, dashboardSrv, FieldsParser[OrganisationDashboard], idMap)
      Success(())
    }
  }

  def warn(message: String, error: Throwable = null): Option[Nothing] = {
    logger.warn(message, error)
    None
  }

  def readFile(name: String): String =
    try Source.fromResource(name).mkString
    catch {
      case _: NullPointerException => sys.error(s"resources/$name : file or directory unreadable")
    }

  def readFile(file: File): String = {
    val source = Source.fromFile(file)
    try source.mkString
    finally source.close()
  }

  //  def readDirectory(name: String): Seq[File] = {
  //    val loader = Thread.currentThread.getContextClassLoader
  //    val url    = Option(loader.getResource(name)).getOrElse(sys.error(s"Directory $name not found"))
  //    val path   = url.getPath
  //    new File(path).listFiles.toSeq
  //  }

  def readJsonFile(path: String): Seq[FObject] =
    try {
      val data = readFile(path)
      for {
        json <- Json
          .parse(data)
          .asOpt[JsValue]
          .orElse(warn(s"File $data has invalid format"))
          .flatMap {
            case arr: JsArray => arr.asOpt[Seq[JsObject]].orElse(warn("Array must contain only object"))
            case o: JsObject  => Some(Seq(o))
            case _            => warn(s"File $data contains data that is not an object nor an array")
          }
          .getOrElse(Nil)
      } yield FObject(json)
    } catch {
      case error: Throwable =>
        logger.warn(s"$path error: ${error.getMessage}")
        Nil
    }

  implicit class RichField(field: Field) {

    def getString(path: String): Option[String] = field.get(path) match {
      case FString(value) => Some(value)
      case _              => None
    }
  }

  def createVertex[V <: Product](
      srv: VertexSrv[V, _],
      parser: FieldsParser[V]
  )(implicit graph: Graph, authContext: AuthContext): Map[String, String] =
    readJsonFile(s"data/${srv.model.label}.json").flatMap { fields =>
      parser(fields - "id")
        .flatMap(e => Or.from(srv.createEntity(e)))
        .map(v => fields.getString("id").map(_ -> v._id))
        .recover(e => warn(s"creation of $fields fails: $e"))
        .get
    }.toMap

  def createEdge[E <: Product, FROM <: Product: ru.TypeTag, TO <: Product: ru.TypeTag](
      srv: EdgeSrv[E, FROM, TO],
      fromSrv: VertexSrv[FROM, _],
      toSrv: VertexSrv[TO, _],
      parser: FieldsParser[E],
      idMap: Map[String, String]
  )(implicit graph: Graph, authContext: AuthContext): Seq[E with Entity] =
    readJsonFile(s"data/${srv.model.label}.json")
      .flatMap { fields =>
        (for {
          fromExtId <- fields.getString("from").toTry(Failure(new Exception("Edge has no from vertex")))
          fromId = idMap.getOrElse(fromExtId, fromExtId)
          from    <- fromSrv.getOrFail(fromId)
          toExtId <- fields.getString("to").toTry(Failure(new Exception("Edge has no to vertex")))
          toId = idMap.getOrElse(toExtId, toExtId)
          to <- toSrv.getOrFail(toId)
          e  <- parser(fields - "from" - "to").fold(e => srv.create(e, from, to), _ => Failure(new Exception("XX")))
        } yield e)
          .fold(t => warn(s"Edge ${srv.model.label} creation fails with: $fields", t), Some(_))
      }
}
