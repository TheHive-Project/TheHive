package org.thp.thehive.migration.th3

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.test.PlaySpecification

class UserNormalisationTest extends PlaySpecification with Conversion {
  override def readAttachment(id: String): Source[ByteString, NotUsed] = Source.empty
  override val mainOrganisation: String                                = "thehive.local"

  "User migration" should {
    "convert simple name" in {
      normaliseLogin("myLogin") must beEqualTo("mylogin@thehive.local")
    }
    "convert simple name with dash" in {
      normaliseLogin("my-Login") must beEqualTo("my-login@thehive.local")
    }
    "convert email address" in {
      normaliseLogin("Firstname.Lastname@Example.com") must beEqualTo("firstname.lastname@example.com")
    }
    "convert login with special characters" in {
      normaliseLogin("login`with\"special^chars%") must beEqualTo("login.with.special.chars@thehive.local")
    }
    "convert login with only special characters" in {
      normaliseLogin("^'\"éç") must beEqualTo("empty.name@thehive.local")
    }
    "convert login with several @" in {
      normaliseLogin("first@second@third") must beEqualTo("first@second.third")
    }
    "convert invalid email address" in {
      normaliseLogin(".first.@.domain.") must beEqualTo("first@domain")
    }
    "convert empty domain" in {
      normaliseLogin("first@") must beEqualTo("first@thehive.local")
    }
    "convert email with invalid domain" in {
      normaliseLogin("first@```") must beEqualTo("first@thehive.local")
    }
    "convert email with dash" in {
      normaliseLogin("-first-name-@-domain-name-") must beEqualTo("first-name@domain-name")
    }
  }
}
