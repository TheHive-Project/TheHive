import sbt.Keys.TaskStreams
import sbt._

import scala.sys.process.{Process, ProcessLogger}

object FileBuilder {

  def prefixLogs(log: ProcessLogger, prefix: String): ProcessLogger = new ProcessLogger {
    override def out(s: ⇒ String): Unit = log.out(s"{$prefix} $s")
    override def err(s: ⇒ String): Unit = log.err(s"{$prefix} $s")
    override def buffer[T](f: ⇒ T): T   = log.buffer(f)
  }

  def apply(label: String, inputFiles: PathFinder, outputFiles: PathFinder, command: (File, String), streams: TaskStreams): Set[File] = {
    val log = prefixLogs(streams.log, label)
    val cache = FileFunction.cached(streams.cacheDirectory / label) { _ ⇒
      streams.log.info(s"$label files have been updated")
      val exitCode = Process(command._2, command._1) ! log
      if (exitCode != 0)
        throw new IllegalStateException(s"$command fails")
      outputFiles.get().toSet
    }
    cache(inputFiles.get().toSet)
  }
}
