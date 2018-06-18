
val h = Map("a" -> "A", "b" -> "B", "c" -> "C")

def formatHeaders(h: Map[String, String]): String = {
  var s: String = ""
  for ((k, v) ← h) (
    s += k + ":" + v + "\n"
  )
  s
}

formatHeaders(h)

