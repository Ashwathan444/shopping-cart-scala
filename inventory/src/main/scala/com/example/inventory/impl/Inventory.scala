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
import com.example.shoppingcart.api.ItemCount
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

  final case class AddItem(itemId: String, quantity: Int, replyTo: ActorRef[Confirmation]) extends InventoryCommand

  final case class UpdateStock(itemId: String, quantity: Int, replyTo: ActorRef[Confirmation]) extends InventoryCommand

  final case class GetAllItems(replyTo: ActorRef[Items]) extends InventoryCommand

  final case class GetItem(replyTo: ActorRef[Items]) extends InventoryCommand

  //INVENTORY REPLIES

  final case class Items(items: Map[String,Int])

  sealed trait Confirmation

  final case class Accepted(item: Items) extends Confirmation

  final case class Rejected(reason: String) extends Confirmation

  implicit val itemFormat: Format[Items]               = Json.format
  implicit val confirmationAcceptedFormat: Format[Accepted] = Json.format
  implicit val confirmationRejectedFormat: Format[Rejected] = Json.format
  implicit val confirmationFormat: Format[Confirmation] = new Format[Confirmation] {
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

  final case class ItemAdded(itemId: String, quantity: Int) extends Event

  final case class StockUpdated(itemId: String, newQuantity: Int) extends Event

  implicit val itemAddedFormat: Format[ItemAdded]                       = Json.format
  implicit val stockUpdatedFormat: Format[StockUpdated]                   = Json.format

  val empty: Inventory= Inventory(items = Map.empty)

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

final case class Inventory(items: Map[String,Int]) {

  import Inventory._

  def applyCommand(cmd: InventoryCommand): ReplyEffect[Event, Inventory] =
    cmd match {
      case AddItem(itemId, quantity, replyTo) => onAddItem(itemId, quantity, replyTo)
      case UpdateStock(itemId, quantity, replyTo) => onUpdateStock(itemId, quantity, replyTo)
      case GetAllItems(replyTo) => onGetAllItems(replyTo)
      case GetItem(replyTo) => onGetItem(replyTo)
    }

  private def onAddItem(
                         itemId: String,
                         quantity: Int,
                         replyTo: ActorRef[Confirmation]
                       ): ReplyEffect[Event, Inventory] = {
    if (items.contains(itemId))
      Effect.reply(replyTo)(Rejected(s"Item '$itemId' was already added to this inventory"))
    else if (quantity <= 0)
      Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))
    else
      Effect
        .persist(ItemAdded(itemId, quantity))
        .thenReply(replyTo)(updatedInventory => Accepted(toItems(updatedInventory)))
  }

  private def onUpdateStock(
                             itemId: String,
                             quantity: Int,
                             replyTo: ActorRef[Confirmation]
                           ): ReplyEffect[Event, Inventory] = {
    if (quantity <= 0)
      Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))
    else if (items.contains(itemId))
      Effect
        .persist(StockUpdated(itemId, quantity))
        .thenReply(replyTo)(updatedInventory => Accepted(toItems(updatedInventory)))
    else
      Effect.reply(replyTo)(Rejected(s"Cannot adjust quantity for item '$itemId'. Item not present."))
  }

  private def onGetAllItems(replyTo: ActorRef[Items]): ReplyEffect[Event, Inventory] = {
    reply(replyTo)(toItems(this))
  }

  private def onGetItem(replyTo: ActorRef[Items]): ReplyEffect[Event, Inventory] = {
    reply(replyTo)(toItems(this))
  }

  private def toItems(inventory: Inventory): Items =
    Items(inventory.items)

  def applyEvent(evt: Event): Inventory =
    evt match {
      case ItemAdded(itemId, quantity)    => onItemAddedOrUpdated(itemId, quantity)
      case StockUpdated(itemId, quantity) => onItemAddedOrUpdated(itemId, quantity)
    }

  private def onItemAddedOrUpdated(itemId: String, quantity: Int): Inventory =
    copy(items = items + (itemId -> quantity))

}

object InventorySerializerRegistry extends JsonSerializerRegistry {

  import Inventory._

  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[Inventory],
    JsonSerializer[ItemAdded],
    JsonSerializer[StockUpdated],
    JsonSerializer[Items],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected],
  )
}



