/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl

import com.google.protobuf.ByteString._
import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.javasdk.workflow.ReflectiveWorkflowProvider
import kalix.javasdk.workflow.Result
import kalix.javasdk.workflow.TestWorkflowSerialization
import kalix.javasdk.impl.workflow.TestWorkflow
import kalix.testkit.TestProtocol
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WorkflowImplSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  import kalix.testkit.workflow.WorkflowMessages._

  "Workflow" should {

    "deserialize response from async call" in {
      val entityId = "1"
      val jsonMessageCodec = new JsonMessageCodec()
      val service = new TestWorkflow(
        ReflectiveWorkflowProvider
          .of[String, TestWorkflowSerialization](
            classOf[TestWorkflowSerialization],
            jsonMessageCodec,
            _ => new TestWorkflowSerialization()))
      val protocol = TestProtocol(service.port)
      val workflow = protocol.workflow.connect()

      workflow.send(init(classOf[TestWorkflowSerialization].getName, entityId))

      workflow.expect(config())

      val emptyState = jsonMessageCodec.encodeScala("empty")
      val stepResult = jsonMessageCodec.encodeScala(new Result.Succeed())

      workflow.send(command(1, entityId, "Start", emptySyntheticRequest("Start")))
      workflow.expect(reply(1, jsonMessageCodec.encodeScala("ok"), emptyState, stepTransition("test")))

      workflow.send(executeStep(2, "test", emptyState))
      workflow.expect(stepExecuted(2, "test", stepResult))

      workflow.send(getNextStep(3, "test", stepResult))
      workflow.expect(end(3, jsonMessageCodec.encodeScala("success")))

      workflow.send(command(1, entityId, "Get", emptySyntheticRequest("Get")))
      workflow.expect(reply(1, jsonMessageCodec.encodeScala("success")))

      protocol.terminate()
      service.terminate()
    }

  }

  private def emptySyntheticRequest(methodName: String) = {
    ScalaPbAny(s"type.googleapis.com/kalix.javasdk.workflow.${methodName}KalixSyntheticRequest", EMPTY)
  }
}
