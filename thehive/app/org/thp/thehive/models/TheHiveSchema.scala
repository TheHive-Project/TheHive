package org.thp.thehive.models

import scala.collection.JavaConverters._

import play.api.inject.Injector

import gremlin.scala.Graph
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models.{InitialValue, ReflectionSchema}
import org.thp.scalligraph.services.VertexSrv
import org.thp.thehive.services.{OrganisationSrv, ProfileSrv, RoleSrv, UserSrv}

@Singleton
class TheHiveSchema @Inject()(injector: Injector) extends ReflectionSchema(getClass.getClassLoader, "org.thp.thehive.models") {

  override lazy val initialValues: Seq[InitialValue[_]] =
    reflectionClasses
      .getSubTypesOf(classOf[VertexSrv[_, _]])
      .asScala
      .filterNot(c ⇒ java.lang.reflect.Modifier.isAbstract(c.getModifiers))
      .toSeq
      .flatMap[InitialValue[_], Seq[InitialValue[_]]] { vertexSrvClass ⇒
        injector.instanceOf(vertexSrvClass).getInitialValues
      }

  override def init(implicit graph: Graph, authContext: AuthContext): Unit = {
    for {
      adminUser           ← injector.instanceOf[UserSrv].getOrFail("admin")
      adminProfile        ← injector.instanceOf[ProfileSrv].getOrFail("admin")
      defaultOrganisation ← injector.instanceOf[OrganisationSrv].getOrFail("default")
    } yield injector.instanceOf[RoleSrv].create(adminUser, defaultOrganisation, adminProfile)
    ()
  }
}
