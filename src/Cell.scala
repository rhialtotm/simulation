/**
 * Created by nathan on 12/9/16.
 * a cell within a piece is either occupied or not.  it also has a color.
 * if it's the game board piece, then it should show unoccupied cells .show
 */
case class Cell(occupied: Boolean = false, color: String, var underline: Boolean = false) {

  // Todo: get rid of var for underLine - i think you could just create a copy of the cell.
  val underLineColor: String = Game.UNDERLINE + color
  val unoccupied: Boolean = !occupied

  override def toString: String = if (this.occupied) "occupied" else "unoccupied"

  def show: String = {
    if (occupied)
      if (underline) underLineColor + Cell.BOX_CHAR else color + Cell.BOX_CHAR
    else
      Cell.unoccupiedColor + Cell.BOX_CHAR
  }

}

object Cell {

  private val BOX_CHAR = "\u25A0" + Game.SANE
  private val unoccupiedColor = Game.BRIGHT_WHITE
  def copy(cell: Cell): Cell = new Cell(cell.occupied, cell.color, cell.underline)

}