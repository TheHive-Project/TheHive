package org.thp.thehive
import play.api.libs.json._

import org.specs2.matcher.{ContainWithResultSeq, Expectable, MatchResult, MatchResultLogicalCombinators, Matcher, TraversableMatchers, ValueCheck}

case class JsonMatcher(expected: JsValue) extends Matcher[JsValue] with MatchResultLogicalCombinators {
  override def apply[S <: JsValue](t: Expectable[S]): MatchResult[S] =
    if (t.value == expected) success("ok", t)
    else {
      (expected, t.value) match {
        case (JsArray(exp), JsArray(value)) ⇒
          val valueChecks: Seq[ValueCheck[JsValue]] = TraversableMatchers.matcherSeqIsContainCheckSeq(value.map(JsonMatcher))
          val expectable: Expectable[Seq[JsValue]]  = t.map(exp)
          val matchResult                           = ContainWithResultSeq(valueChecks).exactly(expectable)
          result(matchResult, t)
        case (JsObject(exp), JsObject(value)) ⇒
          val keys = exp.keySet ++ value.keySet
          val matchResult = keys
            .map(key ⇒ (key, exp.get(key), value.get(key)))
            .map {
              case (key, Some(e), Some(v)) ⇒ JsonMatcher(e)(t.map(v).mapDescription(_ + "." + key))
              case (key, None, Some(v))    ⇒ failure(s"${t.description} has key $key", t.map(v))
              case (key, _, _)             ⇒ failure(s"${t.description} hasn't key $key", t)
            }
            .reduce(_ and _)
          result(matchResult, t)
        case (_, v) ⇒ failure(s"${t.description} !=  $v", t)
      }
    }
}
