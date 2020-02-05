package org.thp.thehive.migration
import java.io.{File, OutputStreamWriter, Writer}

class Terminal(output: Writer) {
  lazy val pathedTput: String = if (new File("/usr/bin/tput").exists()) "/usr/bin/tput" else "tput"

  def consoleDim(s: String): Int = {
    import sys.process._
    Seq("bash", "-c", s"$pathedTput $s 2> /dev/tty").!!.trim.toInt
  }
  def getWidth(): Int                = consoleDim("cols")
  def getHeight(): Int               = consoleDim("lines")
  def control(n: Int, c: Char): Unit = output.write(s"\033[" + n + c)

  /**
    * Move up `n` squares
    */
  def up(n: Int): Unit = if (n != 0) control(n, 'A')

  /**
    * Move down `n` squares
    */
  def down(n: Int): Unit = if (n != 0) control(n, 'B')

  /**
    * Move right `n` squares
    */
  def right(n: Int): Unit = if (n != 0) control(n, 'C')

  /**
    * Move left `n` squares
    */
  def left(n: Int): Unit = if (n != 0) control(n, 'D')

  /**
    * Clear the screen
    *
    * n=0: clear from cursor to end of screen
    * n=1: clear from cursor to start of screen
    * n=2: clear entire screen
    */
  def clearScreen(n: Int): Unit = control(n, 'J')

  /**
    * Clear the current line
    *
    * n=0: clear from cursor to end of line
    * n=1: clear from cursor to start of line
    * n=2: clear entire line
    */
  def clearLine(n: Int): Unit = control(n, 'K')

  def flush(): Unit = output.flush()

  def println(s: String): Unit = output.write(s + "\n")
}

object Terminal {

  // Prefer standard tools. Not sure why we need to do this, but for some
  // reason the version installed by gnu-coreutils blows up sometimes giving
  // "unable to perform all requested operations"
  lazy val pathedStty: String = if (new File("/bin/stty").exists()) "/bin/stty" else "stty"

  def apply[A](body: Terminal => A): A = {
    stty("-a")
    val initialConfig = stty("-g").trim
    try {
      stty("-icanon min 1 -icrnl -inlcr -ixon")
      sttyFailTolerant("dsusp undef")
      stty("-echo")
//      stty("intr undef")
      body(new Terminal(new OutputStreamWriter(System.out)))
    } finally restore(initialConfig)
  }

  private def sttyCmd(s: String) = {
    import sys.process._
    Seq("bash", "-c", s"$pathedStty $s < /dev/tty"): ProcessBuilder
  }

  def stty(s: String): String = sttyCmd(s).!!

  /*
   * Executes a stty command for which failure is expected, hence the return
   * status can be non-null and errors are ignored.
   * This is appropriate for `stty dsusp undef`, since it's unsupported on Linux
   * (http://man7.org/linux/man-pages/man3/termios.3.html).
   */
  def sttyFailTolerant(s: String): Int = sttyCmd(s ++ " 2> /dev/null").!

  def restore(initialConfig: String): Unit = {
    stty(initialConfig)
    ()
  }
}
