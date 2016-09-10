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

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class UserRepositorySpec
    extends WordSpec
    with Matchers
    with BeforeAndAfterAll {
  import UserRepository._

  private implicit val system = ActorSystem()

  private val user = User(0, "jsnow", "Jon Snow", "jsnow@gabbler.io")

  "UserRepository" should {
    "correctly handle getting, adding and removing users" in {
      import user._

      val userRepository     = system.actorOf(UserRepository.props)
      val sender             = TestProbe()
      implicit val senderRef = sender.ref

      userRepository ! GetUsers
      sender.expectMsg(Users(Set.empty))

      userRepository ! AddUser(username, nickname, email)
      sender.expectMsg(UserAdded(user))
      userRepository ! GetUsers
      sender.expectMsg(Users(Set(user)))

      userRepository ! AddUser(username,
                               "Jon Targaryen",
                               "jtargaryen@gabbler.io")
      sender.expectMsg(UsernameTaken(username))

      userRepository ! RemoveUser(id)
      sender.expectMsg(UserRemoved(user))
      userRepository ! GetUsers
      sender.expectMsg(Users(Set.empty))

      userRepository ! RemoveUser(id)
      sender.expectMsg(IdUnknown(id))
    }
  }

  override protected def afterAll() = {
    Await.ready(system.terminate(), 42.seconds)
    super.afterAll()
  }
}
