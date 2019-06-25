package org.thp.cortex.dto.v0

import akka.stream.scaladsl.Source
import akka.util.ByteString

case class Attachment(name: String, size: Long, contentType: String, data: Source[ByteString, _])
