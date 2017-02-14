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

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorSystem, Props, SupervisorStrategy, Terminated }
import akka.cluster.Cluster
import akka.cluster.singleton.{
  ClusterSingletonManager,
  ClusterSingletonManagerSettings,
  ClusterSingletonProxy,
  ClusterSingletonProxySettings
}
import akka.http.scaladsl.model.Uri
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ChatApp {

  private final class Root extends Actor with ActorLogging {

    override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

    private val userRepository = {
      val userEvents =
        Uri(context.system.settings.config.getString("gabbler-chat.user-repository.user-events"))
      val userRepository =
        context.actorOf(
          ClusterSingletonManager.props(UserRepository(userEvents),
                                        NotUsed,
                                        ClusterSingletonManagerSettings(context.system)),
          UserRepository.Name
        )
      context.actorOf(
        ClusterSingletonProxy.props(userRepository.path.elements.mkString("/", "/", ""),
                                    ClusterSingletonProxySettings(context.system)),
        s"${UserRepository.Name}-proxy"
      )
    }

    context.watch(userRepository)
    log.info("gabbler-chat up and running")

    override def receive = {
      case Terminated(actor) =>
        log.error("Terminating the system because {} terminated!", actor.path)
        context.system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("gabbler-chat")
    Cluster(system).registerOnMemberUp(system.actorOf(Props(new Root), "root"))
  }
}
