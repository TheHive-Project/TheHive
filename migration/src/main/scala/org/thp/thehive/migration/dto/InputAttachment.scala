package org.thp.thehive.migration.dto

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString

case class InputAttachment(name: String, size: Long, contentType: String, hashes: Seq[String], data: Source[ByteString, NotUsed])
