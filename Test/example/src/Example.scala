package example
import mainargs.{TokensReader, ParserForMethods, main, arg}
object Example {
  implicit object PathRead extends TokensReader[os.Path](
    "path",
    strs => Right(os.Path(strs.head, os.pwd))
  )
  @main(name = "JSON Reformatter", doc = "Pretty-print JSON or minify it")
  def main(@arg(doc = "Source file to load JSON from; defaults to stdin if not given")
           src: Option[os.Path],
           @arg(doc = "Destination file to write JSON to; defaults to stdout if not given")
           dest: Option[os.Path],
           @arg(doc =
              "Indentation to pretty-print the JSON with; default 4, pass -1 to minify instead")
           indent: Int = 4) = {

    val inputStream = src match{
      case None => System.in
      case Some(s) => os.read.inputStream(s)
    }

    val outputStream = dest match{
      case None => System.out
      case Some(o) => os.write.outputStream(o)
    }

    val writer = new java.io.OutputStreamWriter(outputStream)
    try ujson.reformatTo(inputStream, writer, indent)
    finally writer.flush()
  }

  def main(args: Array[String]): Unit = ParserForMethods(Example).runOrExit(args)
}
