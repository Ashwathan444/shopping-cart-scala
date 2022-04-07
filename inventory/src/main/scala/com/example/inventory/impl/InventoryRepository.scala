package com.example.inventory.impl

import java.time.Instant

import akka.Done
import com.example.inventory.api.InventoryItem
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InventoryRepository(database: Database) {

  class InventoryTable(tag: Tag) extends Table[InventoryItem](tag, "inventory") {
    def itemId = column[String]("item_id", O.PrimaryKey)

    def quantity = column[Int]("quantity")

    def * = (itemId,quantity).mapTo[InventoryItem]
  }

  val inventoryTable = TableQuery[InventoryTable]

  def createTable() = inventoryTable.schema.createIfNotExists

  def findById(id: String): Future[Option[InventoryItem]] =
    database.run(findByIdQuery(id))

  def createItem(itemId: String,quantity: Int): DBIO[Done] = {
    findByIdQuery(itemId)
      .flatMap {
        case None => inventoryTable += InventoryItem(itemId, quantity)
        case _    => DBIO.successful(Done)
      }
      .map(_ => Done)
      .transactionally
  }

  def addStock(itemId: String, q: Int): DBIO[Done] = {
    findByIdQuery(itemId)
      .flatMap {
        case Some(item) => inventoryTable.insertOrUpdate(item.copy(quantity = item.quantity+q))
        // if that happens we have a corrupted system
        // cart checkout can only happens for a existing cart
        case None => throw new RuntimeException(s"Didn't find item to update. CartID: $itemId")
      }
      .map(_ => Done)
      .transactionally
  }

  private def findByIdQuery(cartId: String): DBIO[Option[InventoryItem]] =
    inventoryTable
      .filter(_.itemId === cartId)
      .result
      .headOption

}
