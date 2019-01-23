package org.thp.thehive.models

import java.io.File

import scala.io.Source
import scala.reflect.runtime.{universe ⇒ ru}

import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import gremlin.scala._
import org.thp.scalligraph.auth.{AuthContext, Permission}
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}

case class InitialAuthContext(authContext: AuthContext)

object DatabaseBuilder {
  def build(schema: TheHiveSchema)(implicit db: Database, authContext: AuthContext): Unit = {
    implicit val permissionParser: FieldsParser[Permission] = Permissions.parser

    lazy val logger = Logger(getClass)
    logger.info("Initialize database schema")
    db.createSchemaFrom(schema)
    db.transaction { implicit graph ⇒
      val testUser = schema.userSrv.create(
        User(
          login = authContext.userId,
          name = authContext.userName,
          apikey = None,
          permissions = authContext.permissions,
          status = UserStatus.ok,
          password = None))(graph, authContext)
      val testOrganisation = schema.organisationSrv.create(Organisation(authContext.organisation))
      schema.userSrv.userOrganisationSrv.create(UserOrganisation(), testUser, testOrganisation)
      val idMap = createVertex(schema.caseSrv, FieldsParser[Case]) ++
        createVertex(schema.userSrv, FieldsParser[User]) ++
        createVertex(schema.customFieldSrv, FieldsParser[CustomField]) ++
        createVertex(schema.organisationSrv, FieldsParser[Organisation]) ++
        createVertex(schema.caseTemplateSrv, FieldsParser[CaseTemplate])
      createEdge(schema.caseSrv.caseUserSrv, schema.caseSrv, schema.userSrv, FieldsParser[CaseUser], idMap)
      createEdge(schema.caseSrv.caseImpactStatusSrv, schema.caseSrv, schema.impactStatusSrv, FieldsParser[CaseImpactStatus], idMap)
      createEdge(schema.caseSrv.caseCustomFieldSrv, schema.caseSrv, schema.customFieldSrv, FieldsParser[CaseCustomField], idMap)
      createEdge(schema.caseSrv.caseOrganisationSrv, schema.caseSrv, schema.organisationSrv, FieldsParser[CaseOrganisation], idMap)
      createEdge(schema.caseSrv.caseCaseTemplateSrv, schema.caseSrv, schema.caseTemplateSrv, FieldsParser[CaseCaseTemplate], idMap)
      createEdge(schema.caseSrv.caseResolutionStatus, schema.caseSrv, schema.resolutionStatusSrv, FieldsParser[CaseResolutionStatus], idMap)
      createEdge(
        schema.caseTemplateSrv.caseTemplateCustomFieldSrv,
        schema.caseTemplateSrv,
        schema.customFieldSrv,
        FieldsParser[CaseTemplateCustomField],
        idMap)
      createEdge(
        schema.caseTemplateSrv.caseTemplateOrganisationSrv,
        schema.caseTemplateSrv,
        schema.organisationSrv,
        FieldsParser[CaseTemplateOrganisation],
        idMap)
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
