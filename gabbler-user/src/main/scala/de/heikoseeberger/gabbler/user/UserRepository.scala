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

import akka.actor.{ Actor, ActorLogging, Props }

object UserRepository {

  final case class User(id: Long,
                        username: String,
                        nickname: String,
                        email: String)

  final case object GetUsers
  final case class Users(users: Set[User])

  final case class AddUser(username: String, nickname: String, email: String)
  final case class UsernameTaken(username: String)
  final case class UserAdded(user: User)

  final case class RemoveUser(id: Long)
  final case class IdUnknown(id: Long)
  final case class UserRemoved(user: User)

  final val Name = "user-repository"

  def props: Props = Props(new UserRepository)
}

final class UserRepository extends Actor with ActorLogging {
  import UserRepository._

  private var users = Map.empty[String, User]

  private var nextId = 0L

  override def receive = {
    case GetUsers         => sender() ! Users(users.valuesIterator.to[Set])
    case AddUser(u, n, e) => handleAddUser(u, n, e)
    case RemoveUser(i)    => handleRemoveUser(i)
  }

  private def handleAddUser(username: String,
                            nickname: String,
                            email: String) =
    if (users.contains(username))
      sender() ! UsernameTaken(username)
    else {
      val user = User(nextId, username, nickname, email)
      nextId += 1
      users += username -> user
      log.info(s"Added user with username $username")
      sender() ! UserAdded(user)
    }

  private def handleRemoveUser(id: Long) = {
    def remove(user: User) = {
      users -= user.username
      log.info(s"Removed user with id $id and username ${ user.username }")
      sender() ! UserRemoved(user)
    }
    users.valuesIterator.find(_.id == id) match {
      case None    => sender() ! IdUnknown(id)
      case Some(u) => remove(u)
    }
  }
}
