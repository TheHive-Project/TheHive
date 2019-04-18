//package org.thp.thehive.controllers.v1
//import org.thp.thehive.dto.v1.{ InputShare, OutputShare }
//import org.thp.thehive.models.Share
//import io.scalaland.chimney.dsl._
//
//class ShareConversion {
//  def fromInputShare(inputShare: InputShare): Share =
//    inputShare
//      .into[Share]
//      .transform
//
//  def toOutputShare(richShare: Share): OutputShare =
//    richShare
//      .into[OutputShare]
//      .transform
//}
