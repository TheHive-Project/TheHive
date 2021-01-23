package org.thp.thehive

import org.scalactic.Or
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, Entity, Schema}
import org.thp.scalligraph.services.{EdgeSrv, GenIntegrityCheckOps, VertexSrv}
import org.thp.scalligraph.traversal.Graph
import org.thp.scalligraph.{EntityId, EntityName, RichOption}
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import java.io.File
import javax.inject.{Inject, Singleton}
import scala.io.Source
import scala.reflect.runtime.{universe => ru}
import scala.util.{Failure, Success, Try}

@Singleton
class DatabaseBuilder @Inject() (
    schema: Schema,
    alertSrv: AlertSrv,
    attachmentSrv: AttachmentSrv,
    caseSrv: CaseSrv,
    caseTemplateSrv: CaseTemplateSrv,
    customFieldSrv: CustomFieldSrv,
    dashboardSrv: DashboardSrv,
    dataSrv: DataSrv,
    impactStatusSrv: ImpactStatusSrv,
    keyValueSrv: KeyValueSrv,
    logSrv: LogSrv,
    observableSrv: ObservableSrv,
    observableTypeSrv: ObservableTypeSrv,
    organisationSrv: OrganisationSrv,
    pageSrv: PageSrv,
    patternSrv: PatternSrv,
    procedureSrv: ProcedureSrv,
    profileSrv: ProfileSrv,
    resolutionStatusSrv: ResolutionStatusSrv,
    roleSrv: RoleSrv,
    shareSrv: ShareSrv,
    tagSrv: TagSrv,
    taskSrv: TaskSrv,
    taxonomySrv: TaxonomySrv,
    userSrv: UserSrv,
    integrityChecks: Set[GenIntegrityCheckOps]
) {

  lazy val logger: Logger = Logger(getClass)

  def build()(implicit db: Database, authContext: AuthContext): Try[Unit] = {

    lazy val logger: Logger = Logger(getClass)
    logger.info("Initialize database schema")
    db.createSchemaFrom(schema)
      .flatMap(_ => db.addSchemaIndexes(schema))
      .flatMap { _ =>
        integrityChecks.foreach { check =>
          db.tryTransaction { implicit graph =>
            Success(check.initialCheck())
          }
          ()
        }
        db.tryTransaction { implicit graph =>
          val idMap =
            createVertex(caseSrv, FieldsParser[Case]) ++
              createVertex(alertSrv, FieldsParser[Alert]) ++
              createVertex(attachmentSrv, FieldsParser[Attachment]) ++
              createVertex(caseTemplateSrv, FieldsParser[CaseTemplate]) ++
              createVertex(customFieldSrv, FieldsParser[CustomField]) ++
              createVertex(dashboardSrv, FieldsParser[Dashboard]) ++
              createVertex(dataSrv, FieldsParser[Data]) ++
              createVertex(impactStatusSrv, FieldsParser[ImpactStatus]) ++
              createVertex(keyValueSrv, FieldsParser[KeyValue]) ++
              createVertex(logSrv, FieldsParser[Log]) ++
              createVertex(observableSrv, FieldsParser[Observable]) ++
              createVertex(observableTypeSrv, FieldsParser[ObservableType]) ++
              createVertex(organisationSrv, FieldsParser[Organisation]) ++
              createVertex(pageSrv, FieldsParser[Page]) ++
              createVertex(patternSrv, FieldsParser[Pattern]) ++
              createVertex(procedureSrv, FieldsParser[Procedure]) ++
              createVertex(profileSrv, FieldsParser[Profile]) ++
              createVertex(resolutionStatusSrv, FieldsParser[ResolutionStatus]) ++
              createVertex(roleSrv, FieldsParser[Role]) ++
              createVertex(shareSrv, FieldsParser[Share]) ++
              createVertex(tagSrv, FieldsParser[Tag]) ++
              createVertex(taskSrv, FieldsParser[Task]) ++
              createVertex(taxonomySrv, FieldsParser[Taxonomy]) ++
              createVertex(userSrv, FieldsParser[User])

          createEdge(organisationSrv.organisationOrganisationSrv, organisationSrv, organisationSrv, FieldsParser[OrganisationOrganisation], idMap)
          createEdge(organisationSrv.organisationShareSrv, organisationSrv, shareSrv, FieldsParser[OrganisationShare], idMap)
          createEdge(organisationSrv.organisationTaxonomySrv, organisationSrv, taxonomySrv, FieldsParser[OrganisationTaxonomy], idMap)

          createEdge(taxonomySrv.taxonomyTagSrv, taxonomySrv, tagSrv, FieldsParser[TaxonomyTag], idMap)

          createEdge(roleSrv.userRoleSrv, userSrv, roleSrv, FieldsParser[UserRole], idMap)

          createEdge(shareSrv.shareProfileSrv, shareSrv, profileSrv, FieldsParser[ShareProfile], idMap)
          createEdge(shareSrv.shareObservableSrv, shareSrv, observableSrv, FieldsParser[ShareObservable], idMap)
          createEdge(shareSrv.shareTaskSrv, shareSrv, taskSrv, FieldsParser[ShareTask], idMap)
          createEdge(shareSrv.shareCaseSrv, shareSrv, caseSrv, FieldsParser[ShareCase], idMap)

          createEdge(roleSrv.roleOrganisationSrv, roleSrv, organisationSrv, FieldsParser[RoleOrganisation], idMap)
          createEdge(roleSrv.roleProfileSrv, roleSrv, profileSrv, FieldsParser[RoleProfile], idMap)

//          createEdge(observableSrv.observableKeyValueSrv, observableSrv, keyValueSrv, FieldsParser[ObservableKeyValue], idMap)
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

          createEdge(patternSrv.patternPatternSrv, patternSrv, patternSrv, FieldsParser[PatternPattern], idMap)

          createEdge(procedureSrv.caseProcedureSrv, caseSrv, procedureSrv, FieldsParser[CaseProcedure], idMap)
          createEdge(procedureSrv.procedurePatternSrv, procedureSrv, patternSrv, FieldsParser[ProcedurePattern], idMap)

          Success(())
        }
      }
  }

  def warn(message: String, error: Throwable = null): Option[Nothing] = {
    logger.warn(message, error)
    None
  }

  def readFile(name: String): String = {
    val source = Source.fromResource(name)
    try source.mkString
    catch {
      case _: NullPointerException => sys.error(s"resources/$name : file or directory unreadable")
    } finally source.close()
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
        json <-
          Json
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

    def getString(path: String): Option[String] =
      field.get(path) match {
        case FString(value) => Some(value)
        case _              => None
      }
  }

  def createVertex[V <: Product](
      srv: VertexSrv[V],
      parser: FieldsParser[V]
  )(implicit graph: Graph, authContext: AuthContext): Map[String, EntityId] =
    readJsonFile(s"data/${srv.model.label}.json").flatMap { fields =>
      parser(fields - "id")
        .flatMap(e => Or.from(srv.createEntity(e)))
        .map(v => fields.getString("id").map(_ -> v._id))
        .recover(e => warn(s"creation of $fields fails: $e"))
        .get
    }.toMap

  def createEdge[E <: Product, FROM <: Product: ru.TypeTag, TO <: Product: ru.TypeTag](
      srv: EdgeSrv[E, FROM, TO],
      fromSrv: VertexSrv[FROM],
      toSrv: VertexSrv[TO],
      parser: FieldsParser[E],
      idMap: Map[String, EntityId]
  )(implicit graph: Graph, authContext: AuthContext): Seq[E with Entity] =
    readJsonFile(s"data/${srv.model.label}.json")
      .flatMap { fields =>
        (for {
          fromExtId <- fields.getString("from").toTry(Failure(new Exception("Edge has no from vertex")))
          fromId = idMap.getOrElse(fromExtId, EntityName(fromExtId))
          from    <- fromSrv.getOrFail(fromId)
          toExtId <- fields.getString("to").toTry(Failure(new Exception("Edge has no to vertex")))
          toId = idMap.getOrElse(toExtId, EntityName(toExtId))
          to <- toSrv.getOrFail(toId)
          e  <- parser(fields - "from" - "to").fold(e => srv.create(e, from, to), _ => Failure(new Exception("XX")))
        } yield e)
          .fold(t => warn(s"Edge ${srv.model.label} creation fails with: $fields", t), Some(_))
      }
}
