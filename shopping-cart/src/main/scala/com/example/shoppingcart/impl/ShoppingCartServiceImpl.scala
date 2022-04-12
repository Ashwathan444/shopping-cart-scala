package com.example.shoppingcart.impl

import akka.NotUsed
import com.example.inventory.api.InventoryService
import com.example.shoppingcart.api.{ItemCount, Quantity, ShoppingCartItem, ShoppingCartReport, ShoppingCartService, ShoppingCartView}
import com.example.shoppingcart.impl.ShoppingCart._
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.api.transport.NotFound
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.ExecutionContext
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

import scala.concurrent.duration._
import akka.util.Timeout
import akka.cluster.sharding.typed.scaladsl.EntityRef
import org.slf4j.LoggerFactory

/**
 * Implementation of the `ShoppingCartService`.
 */
class ShoppingCartServiceImpl(
    clusterSharding: ClusterSharding,
    persistentEntityRegistry: PersistentEntityRegistry,
    reportRepository: ShoppingCartReportRepository,
    inventoryService: InventoryService
)(implicit ec: ExecutionContext)
    extends ShoppingCartService {

  private val logger = LoggerFactory.getLogger(this.getClass)
  /**
   * Looks up the shopping cart entity for the given ID.
   */
  private def entityRef(id: String): EntityRef[Command] =
    clusterSharding.entityRefFor(ShoppingCart.typeKey, id)

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def get(id: String): ServiceCall[NotUsed, ShoppingCartView] = ServiceCall { _ =>
    entityRef(id)
      .ask(reply => Get(reply))
      .map(cartSummary => convertShoppingCart(id, cartSummary))
  }

  override def getItemCount(id: String): ServiceCall[NotUsed, ItemCount] = ServiceCall { _ =>
    entityRef(id)
      .ask(reply => GetItemCount(reply))
      .map(cartSummary => getShoppingCartItemCount(id, cartSummary))
  }

  override def addItem(id: String): ServiceCall[ShoppingCartItem, ShoppingCartView] = ServiceCall { update =>
    entityRef(id)
      .ask(reply => AddItem(update.itemId, update.quantity, reply))
      .map { confirmation =>
        confirmationToResult(id, confirmation)
      }
  }

  override def removeItem(id: String, itemId: String): ServiceCall[NotUsed, ShoppingCartView] = ServiceCall { _ =>
    entityRef(id)
      .ask(reply => RemoveItem(itemId, reply))
      .map { confirmation =>
        confirmationToResult(id, confirmation)
      }
  }

  override def removeAll(id: String): ServiceCall[NotUsed, ShoppingCartView] = ServiceCall { _ =>
    entityRef(id)
      .ask(reply => RemoveAll(id, reply))
      .map { confirmation =>
        confirmationToResult(id, confirmation)
      }
  }

  override def adjustItemQuantity(id: String, itemId: String): ServiceCall[Quantity, ShoppingCartView] = ServiceCall {
    update =>
      entityRef(id)
        .ask(reply => AdjustItemQuantity(itemId, update.quantity, reply))
        .map { confirmation =>
          confirmationToResult(id, confirmation)
        }
  }

  override def checkout(id: String): ServiceCall[NotUsed, ShoppingCartView] = ServiceCall { _ =>
    entityRef(id)
      .ask(replyTo => Checkout(replyTo))
      .map { confirmation =>
        confirmationToResult(id, confirmation)
      }
  }

  private def confirmationToResult(id: String, confirmation: Confirmation): ShoppingCartView =
    confirmation match {
      case Accepted(cartSummary) => convertShoppingCart(id, cartSummary)
      case Rejected(reason)      => throw BadRequest(reason)
    }

  override def shoppingCartTopic: Topic[ShoppingCartView] = TopicProducer.taggedStreamWithOffset(Event.Tag) {
    (tag, fromOffset) =>
      persistentEntityRegistry
        .eventStream(tag, fromOffset)
        .filter(rp => rp.event.isInstanceOf[CartCheckedOut])
        .mapAsync(4) { case EventStreamElement(id, _, offset) =>
          logger.info(s"message sent from shopping cart $offset")
          entityRef(id)
            .ask(reply => Get(reply))
            .map(cart => convertShoppingCart(id, cart) -> offset)
        }
  }

  private def convertShoppingCart(id: String, cartSummary: Summary) = {

    ShoppingCartView(
      id,
      cartSummary.items.map((ShoppingCartItem.apply _).tupled).toSeq,
      cartSummary.checkedOut
    )
  }

  private def getShoppingCartItemCount(id: String, cartSummary: Summary) = {

    ItemCount(cartSummary.items.values.sum)
  }

  override def getReport(cartId: String): ServiceCall[NotUsed, ShoppingCartReport] = ServiceCall { _ =>
    reportRepository.findById(cartId).map {
      case Some(cart) => cart
      case None       => throw NotFound(s"Couldn't find a shopping cart report for $cartId")
    }
  }
}
