package org.thp.thehive

import java.nio.file.{Files, Paths}

trait LogFileConfig {
  val configFileLocation = "conf/logback-test.xml"
  Iterator
    .iterate(Paths.get(".").toAbsolutePath)(_.getParent)
    .take(3)
    .find(p => Files.exists(p.resolve(configFileLocation)))
    .fold(println("File conf/logback-test.xml not found")) { p =>
      val absoluteFile = p.resolve(configFileLocation).toFile.getAbsoluteFile.toString
      println(s"Use logger config file $absoluteFile")
      System.setProperty("logger.file", absoluteFile)
      ()
    }
}
