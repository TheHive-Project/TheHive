package org.thp.thehive

import eu.timepit.refined.W
import eu.timepit.refined.api.{RefType, Refined}
import shapeless.{::, HNil}
import eu.timepit.refined.predicates.all._
import org.thp.scalligraph.InvalidFormatAttributeError
import org.thp.scalligraph.controllers.{FNumber, FString}

package object dto {
  type Severity    = Int Refined And[Positive, LessEqual[W.`4`.T]]
  type Tlp         = Int Refined And[NonNegative, LessEqual[W.`3`.T]]
  type Pap         = Int Refined And[NonNegative, LessEqual[W.`3`.T]]
  type Color       = String Refined MatchesRegex[W.`"""^#[0-9a-fA-F]{6,6}|$"""`.T]
  type StringX[N]  = String Refined AllOf[Not[Empty] :: MaxSize[N] :: Not[MatchesRegex[W.`"""[\n\r]"""`.T]] :: HNil]
  type String16    = StringX[W.`16`.T]
  type String32    = StringX[W.`32`.T]
  type String64    = StringX[W.`64`.T]
  type String128   = StringX[W.`128`.T]
  type String512   = StringX[W.`512`.T]
  type Description = String Refined MaxSize[W.`1048576`.T]

  object Severity {
    def apply(value: Int): Severity =
      RefType
        .applyRef[Severity](value)
        .fold(error => throw InvalidFormatAttributeError("severity", error, Set.empty, FNumber(value.toDouble)), identity)
  }

  object Tlp {
    def apply(value: Int): Tlp =
      RefType.applyRef[Tlp](value).fold(error => throw InvalidFormatAttributeError("tlp", error, Set.empty, FNumber(value.toDouble)), identity)
  }

  object Pap {
    def apply(value: Int): Pap =
      RefType.applyRef[Pap](value).fold(error => throw InvalidFormatAttributeError("pap", error, Set.empty, FNumber(value.toDouble)), identity)
  }

  object String64 {
    def apply(name: String, value: String): String64 =
      RefType.applyRef[String64](value).fold(error => throw InvalidFormatAttributeError(name, error, Set.empty, FString(value)), identity)
  }

  object String128 {
    def apply(name: String, value: String): String128 =
      RefType.applyRef[String128](value).fold(error => throw InvalidFormatAttributeError(name, error, Set.empty, FString(value)), identity)
  }

  object String512 {
    def apply(name: String, value: String): String512 =
      RefType.applyRef[String512](value).fold(error => throw InvalidFormatAttributeError(name, error, Set.empty, FString(value)), identity)
  }

  object Description {
    def apply(name: String, value: String): Description =
      RefType.applyRef[Description](value).fold(error => throw InvalidFormatAttributeError(name, error, Set.empty, FString(value)), identity)
  }
}
