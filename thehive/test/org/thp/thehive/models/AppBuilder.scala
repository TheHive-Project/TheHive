package org.thp.thehive.models

import java.io.File

import scala.io.Source
import scala.reflect.ClassTag
import scala.reflect.runtime.{universe ⇒ ru}

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.concurrent.Akka
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.{Application, Logger}

import akka.actor.{Actor, ActorRef, Props}
import com.google.inject.name.Names
import com.google.inject.util.Providers
import gremlin.scala._
import javax.inject.{Inject, Provider, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.thp.scalligraph.InternalError
import org.thp.scalligraph.auth.{ AuthContext, Permission }
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, Entity}
import org.thp.scalligraph.services.{EdgeSrv, VertexSrv}

class AppBuilder extends ScalaModule {

  private var initialized             = false
  private var init: Function[Unit, _] = identity[Unit]

  override def configure(): Unit = {
    init(())
    ()
  }

  def bind[T: Manifest, TImpl <: T: Manifest]: AppBuilder = {
    if (initialized) throw InternalError("Bind is not permitted after app use")
    init = init.andThen(_ ⇒ bind[T].to[TImpl])
    this
  }

  def bindInstance[T: Manifest](instance: T): AppBuilder = {
    if (initialized) throw InternalError("Bind is not permitted after app use")
    init = init.andThen(_ ⇒ bind[T].toInstance(instance))
    this
  }

  def bindEagerly[T: Manifest]: AppBuilder = {
    if (initialized) throw InternalError("Bind is not permitted after app use")
    init = init.andThen(_ ⇒ bind[T].asEagerSingleton())
    this
  }

  def bindToProvider[T: Manifest](provider: Provider[T]): AppBuilder = {
    if (initialized) throw InternalError("Bind is not permitted after app use")
    init = init.andThen(_ ⇒ bind[T].toProvider(provider))
    this
  }

  def bindActor[T <: Actor: ClassTag](name: String, props: Props ⇒ Props = identity): AppBuilder = {
    if (initialized) throw InternalError("Bind is not permitted after app use")
    init = init.andThen { _ ⇒
      bind(classOf[ActorRef])
        .annotatedWith(Names.named(name))
        .toProvider(Providers.guicify(Akka.providerOf[T](name, props)))
        .asEagerSingleton()
    }
    this
  }

  lazy val app: Application = {
    initialized = true
    GuiceApplicationBuilder(modules = Seq(this)).build()
  }

  def instanceOf[T: ClassTag]: T = app.injector.instanceOf[T]
}

object AppBuilder {
  def apply() = new AppBuilder
}

case class InitialAuthContext(authContext: AuthContext)

@Singleton
class DatabaseBuilder @Inject()(db: Database, schema: TheHiveSchema, initialAuthContext: InitialAuthContext) {
  implicit val authContext: AuthContext = initialAuthContext.authContext
  implicit val permissionParser: FieldsParser[Permission] = Permissions.parser

  lazy val logger = Logger(getClass)
  try {
    logger.info("Initialize database schema")
    db.createSchemaFrom(schema)
    db.transaction { implicit graph ⇒
      schema.userSrv.create(
        User(
          login = authContext.userId,
          name = authContext.userName,
          apikey = None,
          permissions = authContext.permissions,
          status = UserStatus.ok,
          password = None))(graph, authContext)
      val idMap = createVertex(schema.caseSrv, FieldsParser[Case]) ++
        createVertex(schema.userSrv, FieldsParser[User]) ++
        createVertex(schema.customFieldSrv, FieldsParser[CustomField])
      createEdge(schema.caseUserSrv, schema.caseSrv, schema.userSrv, FieldsParser[CaseUser], idMap)
      createEdge(schema.caseImpactStatusSrv, schema.caseSrv, schema.impactStatusSrv, FieldsParser[CaseImpactStatus], idMap)
      createEdge(schema.caseCustomFieldSrv, schema.caseSrv, schema.customFieldSrv, FieldsParser[CaseCustomField], idMap)
    }
  } catch {
    case t: Throwable ⇒ t.printStackTrace()
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

  def createVertex[V <: Product](srv: VertexSrv[V], parser: FieldsParser[V])(implicit graph: Graph, authContext: AuthContext): Map[String, String] =
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
      fromSrv: VertexSrv[FROM],
      toSrv: VertexSrv[TO],
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
