/*
 * Copyright 2016 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.heikoseeberger.gabbler.chat

import akka.Done
import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props, Status }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{ HttpEntity, HttpResponse, Uri }
import akka.http.scaladsl.server.Directives
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.testkit.{ TestDuration, TestProbe }
import de.heikoseeberger.akkasse.MediaTypes.`text/event-stream`
import de.heikoseeberger.akkasse.{ EventStreamMarshalling, ServerSentEvent }
import de.heikoseeberger.akkasse.headers.`Last-Event-ID`
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, DurationInt }

object UserRepositorySpec {
  import UserRepository._

  object Server {

    case object Start
    case object Started
    case object Stop

    private def route(minEventId: Int, maxEventId: Int) = {
      import Directives._
      import EventStreamMarshalling._
      get {
        optionalHeaderValueByName(`Last-Event-ID`.name) { lastEventId =>
          try {
            val nextEventId = lastEventId.getOrElse("0").trim.toInt + 1
            complete {
              Source(
                nextEventId
                  .until(nextEventId + 2)
                  .filter(n => n >= minEventId && n <= maxEventId)
              ).map(toServerSentEvent)
            }
          } catch {
            case e: NumberFormatException =>
              complete(
                HttpResponse(
                  BadRequest,
                  entity = HttpEntity(
                    `text/event-stream`,
                    "Integral number expected for Last-Event-ID header!"
                      .getBytes(UTF_8)
                  )
                )
              )
          }
        }
      }
    }
  }

  class Server(address: String, port: Int, minEventId: Int, maxEventId: Int)
      extends Actor
      with ActorLogging {
    import Server._
    import context.dispatcher

    private implicit val mat = ActorMaterializer()

    override def receive = idle

    private def idle: Receive = {
      case Start =>
        Http(context.system)
          .bindAndHandle(route(minEventId, maxEventId), address, port)
          .pipeTo(self)
        context.become(binding(sender()))
    }

    private def binding(recipient: ActorRef): Receive = {
      case b @ Http.ServerBinding(a) =>
        log.info("Listening on {}", a)
        recipient ! Started
        context.become(active(b, Vector.empty))

      case Status.Failure(c) =>
        log.error(c, s"Can't bind to {}:{}!", address, port)
        context.stop(self)
    }

    private def active(serverBinding: Http.ServerBinding,
                       events: Vector[ServerSentEvent]): Receive = {
      case Stop =>
        serverBinding.unbind().map(_ => Done).pipeTo(self)
        context.become(unbinding(serverBinding.localAddress))
    }

    private def unbinding(socketAddress: InetSocketAddress): Receive = {
      case Done =>
        log.info("Stopped listening on {}", socketAddress)
        context.stop(self)

      case Status.Failure(c) =>
        log.error(c, s"Can't unbind from {}!", socketAddress)
        context.stop(self)
    }
  }

  private def toServerSentEvent(n: Int) = {
    import io.circe.generic.auto._
    import io.circe.syntax._
    val user = {
      val id       = (n + 1) / 2
      val username = s"user-$id"
      User(id, username, username)
    }
    val eventType = if (n % 2 == 1) "user-added" else "user-removed"
    ServerSentEvent(user.asJson.noSpaces, eventType, n.toString)
  }
}

class UserRepositorySpec
    extends WordSpec
    with Matchers
    with BeforeAndAfterAll {
  import UserRepository._
  import UserRepositorySpec._

  private implicit val system = ActorSystem()

  private implicit val mat = ActorMaterializer()

  "UserRepository" should {
    "correctly consume user events" in {
      val probe              = TestProbe()
      implicit val senderRef = probe.ref

      val host = "localhost"
      val port = 8888

      val server1 = system.actorOf(
        Props(new Server(host, port, minEventId = 1, maxEventId = 5))
      )
      probe.watch(server1)
      server1 ! Server.Start
      probe.expectMsg(Server.Started)

      val userRepository1 =
        system.actorOf(UserRepository.props(Uri(s"http://$host:$port")))
      probe.within(10.seconds.dilated) {
        probe.awaitAssert {
          userRepository1 ! FindUserByUsername("user-1")
          probe.expectMsg(UsernameUnknown("user-1"))
          userRepository1 ! FindUserByUsername("user-2")
          probe.expectMsg(UsernameUnknown("user-2"))
          userRepository1 ! FindUserByUsername("user-3")
          probe.expectMsg(User(3L, "user-3", "user-3"))
        }
      }
      system.stop(userRepository1)

      server1 ! Server.Stop
      probe.expectTerminated(server1)

      val server2 = system.actorOf(
        Props(new Server(host, port, minEventId = 5, maxEventId = 7))
      )
      probe.watch(server2)
      server2 ! Server.Start
      probe.expectMsg(Server.Started)

      val userRepository2 =
        system.actorOf(UserRepository.props(Uri(s"http://$host:$port")))
      probe.within(10.seconds.dilated) {
        probe.awaitAssert {
          userRepository2 ! FindUserByUsername("user-1")
          probe.expectMsg(UsernameUnknown("user-1"))
          userRepository2 ! FindUserByUsername("user-2")
          probe.expectMsg(UsernameUnknown("user-2"))
          userRepository2 ! FindUserByUsername("user-3")
          probe.expectMsg(UsernameUnknown("user-3"))
          userRepository2 ! FindUserByUsername("user-4")
          probe.expectMsg(User(4L, "user-4", "user-4"))
        }
      }
      system.stop(userRepository2)
    }
  }

  override protected def afterAll() = {
    Await.ready(system.terminate(), Duration.Inf)
    super.afterAll()
  }
}
