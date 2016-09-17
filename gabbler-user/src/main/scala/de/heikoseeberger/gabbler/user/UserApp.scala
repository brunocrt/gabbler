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

package de.heikoseeberger.gabbler.user

import akka.actor.{
  Actor,
  ActorLogging,
  ActorSystem,
  Props,
  SupervisorStrategy,
  Terminated
}
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object UserApp {

  private final class Root extends Actor with ActorLogging {

    override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

    private val userRepository =
      context.actorOf(UserRepository.props, UserRepository.Name)

    private val userApi = {
      val config  = context.system.settings.config
      val address = config.getString("gabbler-user.user-api.address")
      val port    = config.getInt("gabbler-user.user-api.port")
      val timeout = config
        .getDuration("gabbler-user.user-api.user-repository-timeout")
        .asScala
      context.actorOf(UserApi.props(address, port, userRepository, timeout),
                      UserApi.Name)
    }

    context.watch(userRepository)
    context.watch(userApi)
    log.info("gabbler-user up and running")

    override def receive = {
      case Terminated(actor) =>
        log.error(
          s"Terminating the system because ${ actor.path } terminated!"
        )
        context.system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("gabbler-user")
    system.actorOf(Props(new Root), "root")
    Await.ready(system.whenTerminated, Duration.Inf)
  }
}
