package com.example.inventory.impl

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.example.inventory.api.{InventoryItem, InventoryService}
import com.example.inventory.impl.Inventory._
import com.example.shoppingcart.api.{ShoppingCartView,ShoppingCartService}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

/**
 * Implementation of the inventory service.
 */
class InventoryServiceImpl(
    clusterSharding: ClusterSharding,
    persistentEntityRegistry: PersistentEntityRegistry,
    inventoryRepository: InventoryRepository,
    shoppingCartService: ShoppingCartService
)(implicit ec: ExecutionContext) extends InventoryService {



  private def entityRef(itemId: String): EntityRef[InventoryCommand] =
    clusterSharding.entityRefFor(Inventory.typeKey, itemId)

  implicit val timeout: Timeout = Timeout(5.seconds)

  private val logger = LoggerFactory.getLogger(this.getClass)

  override def getItem(itemId: String): ServiceCall[NotUsed, InventoryItem] = ServiceCall { _ =>
    entityRef(itemId)
      .ask(reply => GetItem(itemId: String, reply))
      .map { confirmation =>
        confirmationToResult(itemId, confirmation)
      }
  }


  override def getAllItems(): ServiceCall[NotUsed, List[InventoryItem]] = ServiceCall { _ =>
    inventoryRepository.findAll()
  }

  override def addItem(itemId: String): ServiceCall[ItemDetails, Done] = ServiceCall { item =>
    entityRef(itemId)
      .ask(reply => AddItem(item.name,item.quantity,reply))
      .map(inventory => Done)
  }

  override def updateStock(itemId: String): ServiceCall[Quantity, Done] = ServiceCall { item =>
    entityRef(itemId)
      .ask(reply => UpdateStock(item.quantity,reply))
      .map(inventory => Done)
  }

  override def removeItem(itemId: String): ServiceCall[NotUsed, InventoryItem] = ServiceCall { _ =>
    entityRef(itemId)
      .ask(reply => RemoveItem(itemId: String, reply))
      .map { confirmation =>
        confirmationToResult(itemId, confirmation)
      }
  }

  private def confirmationToResult(id: String, confirmation: Confirmation): InventoryItem =
    confirmation match {
      case Accepted(item)        => convertInventory(id, item)
      case Rejected(reason)      => throw BadRequest(reason)
    }

  override def inventoryTopic: Topic[InventoryItem] = TopicProducer.taggedStreamWithOffset(Event.Tag) {
    (tag, fromOffset) =>
      persistentEntityRegistry
        .eventStream(tag, fromOffset)
        .filter(eventStream => eventStream.event.isInstanceOf[ItemRemoved] || eventStream.event.isInstanceOf[ItemAdded])
        .mapAsync(4) { case EventStreamElement(itemId, _, offset) =>
          logger.info(s"$tag message sent $offset")
          entityRef(itemId)
            .ask(reply => GetItem(itemId,reply))
            .map(item => item match {
              case Accepted(item)   => convertInventory(itemId, item) -> offset)
              case _ => convertInventory(itemId, Item("",0)) -> offset)
            })
        }
  }
  shoppingCartService.shoppingCartTopic.subscribe.atLeastOnce(Flow[ShoppingCartView].map { cart =>
    // Since this is at least once event handling, we really should track by shopping cart, and
    // not update inventory if we've already seen this shopping cart. But this is an in memory
    // inventory tracker anyway, so no need to be that careful.
    cart.items.foreach { item =>
      entityRef(item.itemId)
        .ask(reply => UpdateStock(-item.quantity,reply))
        .map(inventory => Done)
    }
    Done
  })

  private def convertInventory(id: String, item: Item) = {
    InventoryItem(
      id,
      item.name,
      item.quantity
    )
  }

}
