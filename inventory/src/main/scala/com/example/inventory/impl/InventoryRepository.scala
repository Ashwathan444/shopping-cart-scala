package com.example.inventory.impl

import com.example.inventory.api.InventoryItem
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InventoryRepository(database: Database) {

  class InventoryTable(tag: Tag) extends Table[InventoryItem](tag, "inventory") {
    def itemId = column[String]("item_id", O.PrimaryKey)

    def name = column[String]("name")

    def quantity = column[Int]("quantity")

    def reserved = column[Int]("quantity")

    def * = (itemId,name,quantity,reserved).mapTo[InventoryItem]
  }

  val inventoryTable = TableQuery[InventoryTable]

  def createTable() = inventoryTable.schema.createIfNotExists

  def findById(id: String): Future[Option[InventoryItem]] =
    database.run(findByIdQuery(id))

  def findAll(): Future[List[InventoryItem]] =
    database.run(inventoryTable.result).map(rows => rows.toList)

  def createItem(itemId: String, name: String, quantity: Int, reserved: Int) = {
    database.run(addStock(itemId,name,quantity,reserved))
  }

  def removeItem(itemId: String) = {
    database.run(remove(itemId))
  }

  def remove(itemId: String) = {
    inventoryTable.filter(_.itemId === itemId).delete
  }

  def addStock(itemId: String, name:String, quantity: Int, reserved:Int) = {
    inventoryTable.insertOrUpdate(InventoryItem(itemId, name, quantity, reserved))
  }
  def updateStock(itemId: String, quantity: Int) = {

    sqlu"update inventory set quantity = quantity + ${quantity} where item_id::varchar(50) = ${itemId}"
  }


  private def findByIdQuery(itemId: String): DBIO[Option[InventoryItem]] =
    inventoryTable
      .filter(_.itemId === itemId)
      .result
      .headOption

}
