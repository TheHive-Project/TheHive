package services.mappers

import javax.inject.{Inject, Singleton}

import org.elastic4play.controllers.Fields
import play.api.Configuration
import play.api.libs.json.JsValue

import scala.collection.immutable
import scala.concurrent.Future

object MultiUserMapperSrv {

  def getMapper(configuration: Configuration, ssoMapperModules: immutable.Set[UserMapper]): UserMapper = {
    val name = configuration.getOptional[String]("auth.sso.mapper").getOrElse("simple")
    ssoMapperModules.find(_.name == name).get
  }
}

@Singleton
class MultiUserMapperSrv @Inject()(configuration: Configuration, ssoMapperModules: immutable.Set[UserMapper]) extends UserMapper {

  override val name: String           = "usermapper"
  private lazy val mapper: UserMapper = MultiUserMapperSrv.getMapper(configuration, ssoMapperModules)

  override def getUserFields(jsValue: JsValue, authHeader: Option[(String, String)]): Future[Fields] =
    mapper.getUserFields(jsValue, authHeader)

}
