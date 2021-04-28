package org.thp.thehive

import java.nio.file.{Files, Paths}

trait LogFileConfig {
  val configFileLocation = "conf/logback-test.xml"
  Iterator
    .iterate(Paths.get(".").toAbsolutePath.getParent)(_.getParent)
    .take(3)
    .find(p => Files.exists(p.resolve(configFileLocation)))
    .fold(println(s"File conf/logback-test.xml not found (from ${Paths.get(".").toAbsolutePath}")) { p =>
      val absoluteFile = p.resolve(configFileLocation).toFile.getAbsoluteFile.toString
      System.setProperty("logger.file", absoluteFile)
      ()
    }
}
