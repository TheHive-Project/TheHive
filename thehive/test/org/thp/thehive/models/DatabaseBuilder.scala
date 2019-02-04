package org.thp.thehive.models

import java.io.File

import scala.io.Source
import scala.reflect.runtime.{universe ⇒ ru}

import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}
import org.thp.thehive.services._

case class InitialAuthContext(authContext: AuthContext)

@Singleton
class DatabaseBuilder @Inject()(
    schema: TheHiveSchema,
    userSrv: UserSrv,
    organisationSrv: OrganisationSrv,
    caseSrv: CaseSrv,
    customFieldSrv: CustomFieldSrv,
    caseTemplateSrv: CaseTemplateSrv,
    impactStatusSrv: ImpactStatusSrv,
    resolutionStatusSrv: ResolutionStatusSrv) {
  def build()(implicit db: Database, authContext: AuthContext): Unit = {
    implicit val permissionParser: FieldsParser[Permission] = Permissions.parser

    lazy val logger = Logger(getClass)
    logger.info("Initialize database schema")
    db.createSchemaFrom(schema)
    db.transaction { implicit graph ⇒
      val testUser = userSrv.create(
        User(
          login = authContext.userId,
          name = authContext.userName,
          apikey = None,
          permissions = authContext.permissions,
          status = UserStatus.ok,
          password = None))(graph, authContext)
      val testOrganisation = organisationSrv.create(Organisation(authContext.organisation))
      userSrv.userOrganisationSrv.create(UserOrganisation(), testUser, testOrganisation)
      val idMap = createVertex(caseSrv, FieldsParser[Case]) ++
        createVertex(userSrv, FieldsParser[User]) ++
        createVertex(customFieldSrv, FieldsParser[CustomField]) ++
        createVertex(organisationSrv, FieldsParser[Organisation]) ++
        createVertex(caseTemplateSrv, FieldsParser[CaseTemplate])
      createEdge(caseSrv.caseUserSrv, caseSrv, userSrv, FieldsParser[CaseUser], idMap)
      createEdge(caseSrv.caseImpactStatusSrv, caseSrv, impactStatusSrv, FieldsParser[CaseImpactStatus], idMap)
      createEdge(caseSrv.caseCustomFieldSrv, caseSrv, customFieldSrv, FieldsParser[CaseCustomField], idMap)
      createEdge(caseSrv.caseOrganisationSrv, caseSrv, organisationSrv, FieldsParser[CaseOrganisation], idMap)
      createEdge(caseSrv.caseCaseTemplateSrv, caseSrv, caseTemplateSrv, FieldsParser[CaseCaseTemplate], idMap)
      createEdge(caseSrv.caseResolutionStatusSrv, caseSrv, resolutionStatusSrv, FieldsParser[CaseResolutionStatus], idMap)
      createEdge(caseTemplateSrv.caseTemplateCustomFieldSrv, caseTemplateSrv, customFieldSrv, FieldsParser[CaseTemplateCustomField], idMap)
      createEdge(caseTemplateSrv.caseTemplateOrganisationSrv, caseTemplateSrv, organisationSrv, FieldsParser[CaseTemplateOrganisation], idMap)
      ()
    }
  }

  def warn(message: String): Option[Nothing] = {
    println(message)
    None
  }

  def readFile(name: String): String =
    try Source.fromResource(name).mkString
    catch {
      case _: NullPointerException ⇒ sys.error(s"resources/$name : file or directory unreadable")
    }

  def readFile(file: File): String =
    Source.fromFile(file).mkString

  //  def readDirectory(name: String): Seq[File] = {
  //    val loader = Thread.currentThread.getContextClassLoader
  //    val url    = Option(loader.getResource(name)).getOrElse(sys.error(s"Directory $name not found"))
  //    val path   = url.getPath
  //    new File(path).listFiles.toSeq
  //  }

  def readJsonFile(path: String): Seq[FObject] = {
    val data = readFile(path)
    for {
      json ← Json
        .parse(data)
        .asOpt[JsValue]
        .orElse(warn(s"File $data has invalid format"))
        .flatMap {
          case arr: JsArray ⇒ arr.asOpt[Seq[JsObject]].orElse(warn("Array must contain only object"))
          case o: JsObject  ⇒ Some(Seq(o))
          case _            ⇒ warn(s"File $data contains data that is not an object nor an array")
        }
        .getOrElse(Nil)
    } yield FObject(json)
  }

  implicit class RichField(field: Field) {
    def getString(path: String): Option[String] = field.get(path) match {
      case FString(value) ⇒ Some(value)
      case _              ⇒ None
    }
  }

  def createVertex[V <: Product](srv: VertexSrv[V, _], parser: FieldsParser[V])(
      implicit graph: Graph,
      authContext: AuthContext): Map[String, String] =
    readJsonFile(s"data/${srv.model.label}.json").flatMap { fields ⇒
      parser(fields - "id")
        .map(srv.create)
        .map { v ⇒
          fields.getString("id").map(_ → v._id)
        }
        .recover(e ⇒ warn(s"creation of $fields fails: $e"))
        .get
    }.toMap

  def createEdge[E <: Product, FROM <: Product: ru.TypeTag, TO <: Product: ru.TypeTag](
      srv: EdgeSrv[E, FROM, TO],
      fromSrv: VertexSrv[FROM, _],
      toSrv: VertexSrv[TO, _],
      parser: FieldsParser[E],
      idMap: Map[String, String])(implicit graph: Graph, authContext: AuthContext): Seq[E with Entity] =
    readJsonFile(s"data/${srv.model.label}.json")
      .flatMap { fields ⇒
        (for {
          fromExtId ← fields.getString("from").orElse(warn("Edge has no from vertex"))
          fromId = idMap.getOrElse(fromExtId, fromExtId)
          from   = fromSrv.getOrFail(fromId)
          toExtId ← fields.getString("to").orElse(warn(""))
          toId = idMap.getOrElse(toExtId, toExtId)
          to   = toSrv.getOrFail(toId)
          e ← parser(fields - "from" - "to").fold(e ⇒ Some(srv.create(e, from, to)), _ ⇒ warn(""))
        } yield e)
          .orElse(warn(s"Edge ${srv.model.label} creation fails with: $fields"))
      }
}
