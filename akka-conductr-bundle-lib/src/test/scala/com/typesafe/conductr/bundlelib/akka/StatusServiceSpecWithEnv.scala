/*
 * Copyright © 2014-2015 Typesafe, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Typesafe, Inc.
 */

package com.typesafe.conductr.bundlelib.akka

import java.net.{ InetSocketAddress, URL }

import akka.http.Http
import akka.http.model.StatusCodes
import akka.http.server.Directives._
import akka.stream.ActorFlowMaterializer
import akka.testkit.TestProbe
import com.typesafe.conductr.bundlelib.scala.Env
import com.typesafe.conductr.{ AkkaUnitTest, _ }

import scala.concurrent.Await
import scala.util.{ Failure, Success }

class StatusServiceSpecWithEnv extends AkkaUnitTest("StatusServiceSpecWithEnv", "akka.loglevel = INFO") {

  "The StatusService functionality in the library" should {
    "be able to call the right URL to signal that it is up" in {

      implicit val cc = ConnectionContext(system)

      val probe = new TestProbe(system)

      import system.dispatcher
      import cc.actorFlowMaterializer

      val handler =
        path("bundles" / Segment) { bundleId =>
          put {
            parameter('isStarted ! "true") {
              complete {
                probe.ref ! bundleId
                StatusCodes.NoContent
              }
            }
          }
        }

      val url = new URL(Env.conductRStatus.get)
      val server = Http(system).bindAndStartHandlingWith(handler, url.getHost, url.getPort, settings = None)

      try {
        server.onComplete {
          case Success(binding) => probe.ref ! binding.localAddress
          case Failure(e)       => probe.ref ! e
        }

        val address = probe.expectMsgType[InetSocketAddress]
        address.getHostString should be(url.getHost)
        address.getPort should be(url.getPort)

        Await.result(StatusService.signalStarted(), timeout.duration).isDefined shouldBe true

        val receivedId = probe.expectMsgType[String]
        receivedId should be(Env.bundleId.get)

      } finally {
        server.foreach(_.unbind())
      }
    }
  }
}
