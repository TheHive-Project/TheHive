package example
import utest._
object ExampleTests extends TestSuite{
  val tests = Tests{

    val src = os.pwd / "example" / "test" / "resources" /  "src.json"
    test("minified") {

      val destMinified = os.temp()
      Example.main(src = Some(src), dest = Some(destMinified), indent = -1)
      assert(
        os.read(destMinified) ==
        """{"person1":{"name":"Alice","welcome":"Hello Alice!"},"person2":{"name":"Bob","welcome":"Hello Bob!"}}"""
      )
    }
    test("indent0") {
      val destIndent0 = os.temp()
      Example.main(src = Some(src), dest = Some(destIndent0), indent = 0)
      assert(
        os.read(destIndent0) ==
        """|{
           |"person1": {
           |"name": "Alice",
           |"welcome": "Hello Alice!"
           |},
           |"person2": {
           |"name": "Bob",
           |"welcome": "Hello Bob!"
           |}
           |}""".stripMargin
      )
    }
    test("indent2"){
      val destIndent2 = os.temp()
      Example.main(src = Some(src), dest = Some(destIndent2), indent = 2)

      assert(
        os.read(destIndent2) ==
        """{
          |  "person1": {
          |    "name": "Alice",
          |    "welcome": "Hello Alice!"
          |  },
          |  "person2": {
          |    "name": "Bob",
          |    "welcome": "Hello Bob!"
          |  }
          |}""".stripMargin
      )
    }
  }
}
