package com.example.inventory.impl

import java.time.Instant
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.Effect.reply
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTagger
import com.lightbend.lagom.scaladsl.persistence.AkkaTaggerAdapter
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import play.api.libs.json.Format
import play.api.libs.json._

import scala.collection.immutable.Seq

object Inventory {

  //INVENTORY COMMANDS

  trait CommandSerializable

  sealed trait InventoryCommand extends CommandSerializable

  final case class AddItem(name: String, quantity: Int, replyTo: ActorRef[Confirmation]) extends InventoryCommand

  final case class UpdateStock(quantity: Int, replyTo: ActorRef[Confirmation]) extends InventoryCommand

  final case class RemoveItem(itemId: String, replyTo: ActorRef[Confirmation]) extends InventoryCommand

  final case class GetAllItems(replyTo: ActorRef[Item]) extends InventoryCommand

  final case class GetItem(itemId: String, replyTo: ActorRef[Confirmation]) extends InventoryCommand

  //INVENTORY REPLIES

  final case class Item(name: String,quantity: Int)

  sealed trait Confirmation

  final case class Accepted(item: Item) extends Confirmation

  final case class Rejected(reason: String) extends Confirmation

  implicit val itemFormat: Format[Item]                     = Json.format
  implicit val confirmationAcceptedFormat: Format[Accepted] = Json.format
  implicit val confirmationRejectedFormat: Format[Rejected] = Json.format
  implicit val confirmationFormat: Format[Confirmation]     = new Format[Confirmation] {
    override def reads(json: JsValue): JsResult[Confirmation] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[Rejected](json)
      else
        Json.fromJson[Accepted](json)
    }

    override def writes(o: Confirmation): JsValue = {
      o match {
        case acc: Accepted => Json.toJson(acc)
        case rej: Rejected => Json.toJson(rej)
      }
    }
  }

  // INVENTORY EVENTS
  sealed trait Event extends AggregateEvent[Event] {
    override def aggregateTag: AggregateEventTagger[Event] = Event.Tag
  }

  object Event {
    val Tag: AggregateEventShards[Event] = AggregateEventTag.sharded[Event](numShards = 10)
  }

  final case class ItemAdded(name: String, quantity: Int) extends Event

  final case class StockUpdated(name: String,newQuantity: Int) extends Event

  final case class ItemRemoved(itemId: String) extends Event

  implicit val itemAddedFormat: Format[ItemAdded]                       = Json.format
  implicit val stockUpdatedFormat: Format[StockUpdated]                 = Json.format
  implicit val itemRemovedFormat: Format[ItemRemoved]                       = Json.format

  val empty: Inventory= Inventory("",0)

  val typeKey: EntityTypeKey[InventoryCommand] = EntityTypeKey[InventoryCommand]("Inventory")

  def apply(persistenceId: PersistenceId): EventSourcedBehavior[InventoryCommand, Event, Inventory] = {
    EventSourcedBehavior
      .withEnforcedReplies[InventoryCommand, Event, Inventory](
        persistenceId = persistenceId,
        emptyState = Inventory.empty,
        commandHandler = (inv, cmd) => inv.applyCommand(cmd),
        eventHandler = (inv, evt) => inv.applyEvent(evt)
      )
  }

  def apply(entityContext: EntityContext[InventoryCommand]): Behavior[InventoryCommand] =
    apply(PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, Event.Tag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))

  implicit val inventoryFormat: Format[Inventory] = Json.format

}

final case class Inventory(name: String, quantity: Int) {

  import Inventory._
  def applyCommand(cmd: InventoryCommand): ReplyEffect[Event, Inventory] =
    cmd match {
      case AddItem(name, quantity, replyTo) => onAddItem(name, quantity, replyTo)
      case UpdateStock(quantity, replyTo) => onUpdateStock(quantity, replyTo)
      case RemoveItem(id, replyTo) => onRemoveItem(id, replyTo)
      case GetAllItems(replyTo) => onGetAllItems(replyTo)
      case GetItem(id,replyTo) => onGetItem(id,replyTo)
    }

  private def onAddItem(
                         name: String,
                         quantity: Int,
                         replyTo: ActorRef[Confirmation]
                       ): ReplyEffect[Event, Inventory] = {
    if (quantity <= 0)
      Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))
    else
      Effect
        .persist(ItemAdded(name, quantity))
        .thenReply(replyTo)(updatedInventory => Accepted(toItems(updatedInventory)))
  }

  private def onUpdateStock(
                             quantity: Int,
                             replyTo: ActorRef[Confirmation]
                           ): ReplyEffect[Event, Inventory] = {
    if (quantity+this.quantity < 0)
      Effect.reply(replyTo)(Rejected("Quantity must be lesser"))
    else
      Effect
        .persist(StockUpdated(name,quantity))
        .thenReply(replyTo)(updatedInventory => Accepted(toItems(updatedInventory)))

  }

  private def onRemoveItem(
                         id: String,
                         replyTo: ActorRef[Confirmation]
                       ): ReplyEffect[Event, Inventory] = {
    if (name == "")
      Effect.reply(replyTo)(Rejected("Item is not added"))
    else
      Effect
        .persist(ItemRemoved(id))
        .thenReply(replyTo)(updatedInventory => Accepted(toItems(updatedInventory)))

  }

  private def onGetAllItems(replyTo: ActorRef[Item]): ReplyEffect[Event, Inventory] = {
    reply(replyTo)(toItems(this))
  }

  private def onGetItem(id: String, replyTo: ActorRef[Confirmation]): ReplyEffect[Event, Inventory] = {
    if (name == "")
      Effect.reply(replyTo)(Rejected("Item is not found"))
    else
      Effect.reply(replyTo)(Accepted(toItems(this)))
  }

  private def toItems(inventory: Inventory): Item =
    Item(inventory.name,inventory.quantity)

  def applyEvent(evt: Event): Inventory =
    evt match {
      case ItemAdded(name, quantity)      => onItemAdded(name, quantity)
      case StockUpdated(name,quantity)    => onItemUpdated(name, quantity)
      case ItemRemoved(id)                => onItemRemoved()
    }

  private def onItemAdded(name: String, quantity: Int): Inventory = {
    copy(name, quantity)
  }

  private def onItemUpdated(name: String, quantity: Int): Inventory = {
    copy(name, quantity+this.quantity)
  }

  private def onItemRemoved(): Inventory = {
    copy("",0)
  }

}

object InventorySerializerRegistry extends JsonSerializerRegistry {

  import Inventory._

  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[Inventory],
    JsonSerializer[ItemAdded],
    JsonSerializer[StockUpdated],
    JsonSerializer[ItemRemoved],
    JsonSerializer[Item],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected],
  )
}



