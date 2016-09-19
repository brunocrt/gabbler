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

import akka.NotUsed
import akka.actor.{ ActorLogging, Props }
import akka.persistence.PersistentActor
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
import akka.stream.scaladsl.Source

object UserRepository {

  sealed trait UserEvent

  final case object GetUsers
  final case class Users(users: Set[User])

  final case class AddUser(username: String, nickname: String, email: String)
  final case class UserAdded(user: User) extends UserEvent
  final case class UsernameTaken(username: String)

  final case class RemoveUser(id: Long)
  final case class UserRemoved(user: User) extends UserEvent
  final case class IdUnknown(id: Long)

  final case class GetUserEvents(fromSeqNo: Long)
  final case class UserEvents(userEvents: Source[(Long, UserEvent), NotUsed])

  final case class User(id: Long, username: String, nickname: String, email: String)

  final val Name = "user-repository"

  def apply(readJournal: EventsByPersistenceIdQuery): Props =
    Props(new UserRepository(readJournal))
}

final class UserRepository(readJournal: EventsByPersistenceIdQuery)
    extends PersistentActor
    with ActorLogging {
  import UserRepository._

  override val persistenceId = Name

  private var users = Map.empty[String, User]

  override def receiveCommand = {
    case GetUsers                           => sender() ! Users(users.valuesIterator.to[Set])
    case AddUser(username, nickname, email) => handleAddUser(username, nickname, email)
    case RemoveUser(id)                     => handleRemoveUser(id)
    case GetUserEvents(fromSeqNo)           => handleGetUserEvents(fromSeqNo)
  }

  override def receiveRecover = {
    case UserAdded(user)   => users += user.username -> user
    case UserRemoved(user) => users -= user.username
  }

  private def handleAddUser(username: String, nickname: String, email: String) = {
    def add() =
      persist(UserAdded(User(lastSequenceNr, username, nickname, email))) { userAdded =>
        receiveRecover(userAdded)
        log.info("Added user with username {}", username)
        sender() ! userAdded
      }
    if (!users.contains(username)) add() else sender() ! UsernameTaken(username)
  }

  private def handleRemoveUser(id: Long) = {
    def remove(user: User) =
      persist(UserRemoved(user)) { userRemoved =>
        receiveRecover(userRemoved)
        log.info("Removed user with id {} and username {}", id, user.username)
        sender() ! userRemoved
      }
    users.valuesIterator.find(_.id == id) match {
      case Some(user) => remove(user)
      case None       => sender() ! IdUnknown(id)
    }
  }

  private def handleGetUserEvents(fromSeqNo: Long) = {
    val userEvents =
      readJournal
        .eventsByPersistenceId(Name, fromSeqNo, Long.MaxValue)
        .collect { case EventEnvelope(_, _, seqNo, event: UserEvent) => seqNo -> event }
    sender() ! UserEvents(userEvents)
  }
}
