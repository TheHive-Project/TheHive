package org.thp.thehive.controllers.dav

import gremlin.scala.{Graph, Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.thehive.services.CaseSrv
import org.thp.scalligraph.steps.StepsOps._

@Singleton
class VFS @Inject()(caseSrv: CaseSrv) {

  def get(path: List[String])(implicit graph: Graph, authContext: AuthContext): List[Resource] = path match {
    case Nil | "" :: Nil       => List(StaticResource(""))
    case "cases" :: Nil        => List(StaticResource(""))
    case "cases" :: cid :: Nil => caseSrv.get(cid).toList.map(EntityResource(_, emptyId = true))
    case "cases" :: cid :: aid :: Nil =>
      caseSrv.get(cid).observables.attachments.has(Key[String]("attachmentId"), P.eq(aid)).toList.map(AttachmentResource(_, emptyId = true))
    case _ => Nil
  }

  def list(path: List[String])(implicit graph: Graph, authContext: AuthContext): List[Resource] = path match {
    case Nil | "" :: Nil => List(StaticResource("cases"))
    case "cases" :: Nil  => caseSrv.initSteps.visible.toList.map(EntityResource(_, emptyId = false))
    case "cases" :: id :: Nil =>
      caseSrv
        .get(id)
        .observables
        .attachments
        .map(AttachmentResource(_, emptyId = false))
        .toList
    case _ => Nil
  }
}
