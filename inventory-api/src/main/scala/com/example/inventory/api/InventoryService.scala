package com.example.inventory.api

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.KafkaProperties
import com.lightbend.lagom.scaladsl.api.broker.kafka.PartitionKeyStrategy
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.JsValueMessageSerializer
import com.lightbend.lagom.scaladsl.api.transport.Method
import play.api.libs.json.{Format, Json}


object InventoryService {
  val TOPIC_NAME = "inventory"
}

trait InventoryService extends Service {

  /**
   * Get the inventory level for the given item id.
   */
  def getItem(itemId: String): ServiceCall[NotUsed, InventoryItem]

  def getAllItems(): ServiceCall[NotUsed, List[InventoryItem]]
  /**
   * Add inventory to the given item id.
   */
  def updateStock(itemId: String): ServiceCall[Quantity, Done]

  def addItem(itemId: String): ServiceCall[ItemDetails, Done]

  def removeItem(itemId: String): ServiceCall[NotUsed, InventoryItem]

  def inventoryTopic: Topic[InventoryItem]

  final override def descriptor = {
    import Service._

    named("inventory")
      .withCalls(
        restCall(Method.GET, "/inventory/:itemId", getItem _),
        restCall(Method.GET, "/inventory", getAllItems _),
        restCall(Method.PUT, "/inventory/:itemId", updateStock _),
        restCall(Method.POST, "/inventory/:itemId", addItem _),
        restCall(Method.DELETE, "/inventory/:itemId", removeItem _)
      )
      .withTopics(
        topic(InventoryService.TOPIC_NAME, inventoryTopic)
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[InventoryItem](_.itemId)
          )
      )
      .withAutoAcl(true)
  }

  case class Quantity(quantity: Int)

  object Quantity{
    implicit val format: Format[Quantity] = Json.format
  }

  case class ItemDetails(name: String, quantity: Int)

  object ItemDetails{
    implicit val format: Format[ItemDetails] = Json.format
  }

}

final case class InventoryItem(itemId: String,name: String, quantity: Int)

object InventoryItem {
  implicit val format: Format[InventoryItem] = Json.format

  // For case classes with hand-written companion objects, .tupled only works if
  // you manually extend the correct Scala function type. See SI-3664 and SI-4808.
  def tupled(t: (String, String, Int)) = InventoryItem(t._1, t._2, t._3)
}





