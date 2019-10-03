package org.thp.misp.client

import java.util.Base64

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

object Base64Flow {
  def encode(): Flow[ByteString, ByteString, NotUsed] = Flow.fromGraph(new Base64EncoderFlow)
}

class Base64EncoderFlow extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in: Inlet[ByteString]                             = Inlet[ByteString]("in")
  val out: Outlet[ByteString]                           = Outlet[ByteString]("out")
  override def shape: FlowShape[ByteString, ByteString] = FlowShape.of(in, out)
  val encoder: Base64.Encoder                           = Base64.getEncoder

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    var remainingBytes: ByteString = ByteString.empty

    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          val data = (remainingBytes ++ grab(in)).toArray[Byte]
          val r    = data.length % 3
          if (r == 0)
            push(out, ByteString(encoder.encode(data)))
          else {
            remainingBytes = ByteString(data.drop(data.length - r))
            push(out, ByteString(encoder.encode(data.take(data.length - r))))
          }
        }

        override def onUpstreamFinish(): Unit = {
          push(out, ByteString(encoder.encode(remainingBytes.toArray)))
          completeStage
        }
      }
    )
    setHandler(out, new OutHandler {
      override def onPull(): Unit =
        pull(in)
    })
  }
}
