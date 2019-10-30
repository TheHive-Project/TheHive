package org.thp.thehive.controllers.dav

import gremlin.scala.{Graph, Key, P}
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.thehive.services.CaseSrv
import org.thp.scalligraph.steps.StepsOps._

@Singleton
class VFS @Inject()(caseSrv: CaseSrv) {

  def get(path: List[String])(implicit graph: Graph, authContext: AuthContext): List[Resource] = path match {
    case Nil | "" :: Nil                        => List(StaticResource(""))
    case "cases" :: Nil                         => List(StaticResource(""))
    case "cases" :: cid :: Nil                  => caseSrv.initSteps.getByNumber(cid.toInt).toList.map(EntityResource(_, ""))
    case "cases" :: cid :: "observables" :: Nil => List(StaticResource(""))
    case "cases" :: cid :: "tasks" :: Nil       => List(StaticResource(""))
    case "cases" :: cid :: "observables" :: aid :: Nil =>
      caseSrv
        .initSteps
        .getByNumber(cid.toInt)
        .observables
        .attachments
        .has(Key[String]("attachmentId"), P.eq(aid))
        .toList
        .map(AttachmentResource(_, emptyId = true))
    case "cases" :: cid :: "tasks" :: aid :: Nil =>
      caseSrv
        .initSteps
        .getByNumber(cid.toInt)
        .tasks
        .logs
        .attachments
        .has(Key[String]("attachmentId"), P.eq(aid))
        .toList
        .map(AttachmentResource(_, emptyId = true))
    case _ => Nil
  }

  def list(path: List[String])(implicit graph: Graph, authContext: AuthContext): List[Resource] = path match {
    case Nil | "" :: Nil       => List(StaticResource("cases"))
    case "cases" :: Nil        => caseSrv.initSteps.visible.toList.map(c => EntityResource(c, c.number.toString))
    case "cases" :: cid :: Nil => List(StaticResource("observables"), StaticResource("tasks"))
    case "cases" :: cid :: "observables" :: Nil =>
      caseSrv
        .initSteps
        .getByNumber(cid.toInt)
        .observables
        .attachments
        .map(AttachmentResource(_, emptyId = false))
        .toList
    case "cases" :: cid :: "tasks" :: Nil =>
      caseSrv
        .initSteps
        .getByNumber(cid.toInt)
        .tasks
        .logs
        .attachments
        .map(AttachmentResource(_, emptyId = false))
        .toList
    case _ => Nil
  }
}
