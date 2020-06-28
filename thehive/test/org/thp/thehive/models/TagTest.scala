package org.thp.thehive.models

import play.api.test.PlaySpecification

class TagTest extends PlaySpecification {
  val defaultNamespace: String = "_default_namespace_"
  val defaultColor: Int        = 0xffff00

  def parseTag(s: String): Tag = Tag.fromString(s, defaultNamespace, defaultColor)
  "tag" should {
    "be parsed from key:value" in {
      val tag = parseTag("Module:atest_blah_blah")
      tag must beEqualTo(Tag(defaultNamespace, "Module", Some("atest_blah_blah"), None, defaultColor))
      tag.toString must beEqualTo("Module=\"atest_blah_blah\"")
    }

    "be parsed from key:value=" in {
      val tag = parseTag("Id:7SeUoB3IBABD+tMh2PjVJYg==")
      tag must beEqualTo(Tag(defaultNamespace, "Id", Some("7SeUoB3IBABD+tMh2PjVJYg=="), None, defaultColor))
      tag.toString must beEqualTo("Id=\"7SeUoB3IBABD+tMh2PjVJYg==\"")
    }

    "be parsed from key: value" in {
      val tag = parseTag("domain: google.com")
      tag must beEqualTo(Tag(defaultNamespace, "domain", Some("google.com"), None, defaultColor))
      tag.toString must beEqualTo("domain=\"google.com\"")
    }

    "be parsed from key: a.b.c.d" in {
      val tag = parseTag("ip: 8.8.8.8")
      tag must beEqualTo(Tag(defaultNamespace, "ip", Some("8.8.8.8"), None, defaultColor))
      tag.toString must beEqualTo("ip=\"8.8.8.8\"")
    }

    "be parsed with colour" in {
      val tag = parseTag("ip:8.8.8.8#FF00FF")
      tag must beEqualTo(Tag(defaultNamespace, "ip", Some("8.8.8.8"), None, 0xFF00FF))
      tag.toString must beEqualTo("ip=\"8.8.8.8\"")
    }

    "be parsed with hash sign and colour" in {
      val tag = parseTag("case:#42#FF00FF")
      tag must beEqualTo(Tag(defaultNamespace, "case", Some("#42"), None, 0xFF00FF))
      tag.toString must beEqualTo("case=\"#42\"")
    }
  }
}
