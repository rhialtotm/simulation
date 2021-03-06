/**
 * Created by nathan on 1/11/17.
 * my attempt to use bit operations to speed up this damn thing
 * which definitely worked for open lines and max contiguous lines
 */

/**
 * in a separate test, running 500,000 loops of a case class vs. a Tuple2, of two ints,
 * each loop creating an instance of the case class or the Tuple2
 * then accessing each value to populate a var with the sum from 0 to 1000
 * the case class ran 36% faster than the Tuple2.
 * results were pretty amazing - a 30% speed up in the overall simulations/second for a simple refactoring!
 * just for using this (Loc) bad boy everywhere!
 */
case class Loc(row: Int, col: Int) {
  def show: String = "(" + row + "," + col + ")"
}

/**
 * The OccupancyGrid!
 */
case class OccupancyGrid(
  rows:          Int,
  cols:          Int,
  rowGrid:       Array[Long],
  colGrid:       Array[Long],
  occupancyGrid: Array[Array[Boolean]],
  boardSizeInfo: BoardSizeInfo
) {

  override def toString: String = {

    rowGrid.zipWithIndex.map { row =>

      val seq = (0 until cols) map { x => getBitAt(row._2, x) }

      "[" + seq.mkString + "]"
    }.mkString("\n")

  }

  private val boardSize = rows

  def getOccupancyGrid: Array[Array[Boolean]] = occupancyGrid

  def popCount: Int = {
    var row = 0
    var count = 0
    while (row < rows) {
      val rowVal = rowGrid(row).toInt
      count += boardSizeInfo.popTable(rowVal)
      row += 1
    }
    count
  }

  def asBigInt: math.BigInt = {

    // todo one might consider testing this

    var big = math.BigInt(0)

    var row = 0
    while (row < boardSize) {
      val rowVal = rowGrid(row)

      if (rowVal > 0) {
        val shift = row * boardSize
        val shifted = math.BigInt(rowVal) << shift
        big = big + shifted
      }

      row += 1
    }

    big
  }

  def copy: OccupancyGrid = {

    // using clone vs. map reduced % of total execution time spent in .copy from 18% to 2%
    val newRowGrid = rowGrid.clone
    val newColGrid = colGrid.clone

    val newOccupancyGrid = {

      val newOccupancyGrid = new Array[Array[Boolean]](occupancyGrid.length)

      // again, substantially faster to clone than any other way
      var i = 0
      while (i < occupancyGrid.length) {
        newOccupancyGrid(i) = occupancyGrid(i).clone
        i += 1
      }
      newOccupancyGrid
    }

    OccupancyGrid(rows, cols, newRowGrid, newColGrid, newOccupancyGrid, boardSizeInfo)

  }

  /**
   * rotate both transposes and reverses columns
   * it's enough to know it's figured out for this one off case
   * don't care much about a generic answer right now
   */
  def rotate: OccupancyGrid = {

    def rotateImpl(rotateMe: Array[Long]): Array[Long] = {

      val newGrid = new Array[Long](cols)

      val iOffsetStart = 0
      val jOffsetStart = rows - 1

      var iOffset = iOffsetStart
      var jOffset = jOffsetStart

      var i = 0
      var j = 0

      while (i < rows) {
        while (j < cols) {

          val value = getBitAt(rotateMe, i, j)

          if (value == 1) {
            newGrid(i + iOffset) |= (value << (j + jOffset))
          }

          iOffset += 1
          jOffset -= 1
          j += 1
        }

        j = 0
        i += 1
        iOffset = iOffsetStart - i
        jOffset = jOffsetStart - i

      }

      newGrid
    }

    val newRowGrid = rotateImpl(rowGrid)
    val newColGrid = rotateImpl(colGrid)

    val newOccupancyGrid = occupancyGrid.transpose.map(_.reverse)

    OccupancyGrid(cols, rows, newRowGrid, newColGrid, newOccupancyGrid, boardSizeInfo)

    /*
    for {
      i <- rows
      j <- cols
    } {

      if occupied(i,j)

        occupy(i, j+2)
        occupy(i+1,j+1)
        occupy(i+2,j+0)

        occupy(i-1, j+1)
        occupy(i +0, j+0)
        occupy(i+1, j-1)

        occupy(i-2, j)
        occupy(i-1, j-1)
        occupy(i-0, j-2)

      else
        unoccupy(i, j+2)

      100 (0,2) (1,2) (2,2)
      100 (0,1) (1,1) (2,1)
      111 (0,0) (1,0) (2,0)

      111
      100
      100

      111 (0,0) (1,0) (2,0)

      1
      1
      1
*/
  }

  def clearRow(row: Int): Unit = {
    var col = 0
    while (col < cols) {
      unSetBitAt(colGrid, col, row)
      occupancyGrid(row)(col) = false
      col += 1
    }
    // it's an optimization to not just use unoccupy
    // as we can zero out the rowGrid in one fell swoop
    rowGrid(row) = boardSizeInfo.anySizeEmptyLineValue
  }

  def clearCol(col: Int): Unit = {
    var row = 0
    while (row < rows) {
      unSetBitAt(rowGrid, row, col)
      occupancyGrid(row)(col) = false
      row += 1
    }
    // it's an optimization to not just use unoccupy
    // as we can zero out the colGrid in one fell swoop
    colGrid(col) = boardSizeInfo.anySizeEmptyLineValue
  }

  def openLineCount: Int = getOpenLineCount(rowGrid) + getOpenLineCount(colGrid)

  private def getOpenLineCount(theGrid: Array[Long]): Int = {
    var i = 0
    var count = 0
    while (i < theGrid.length) {
      if (theGrid(i) == boardSizeInfo.anySizeEmptyLineValue) { count += 1 }
      i += 1
    }
    count
  }

  def lineContiguousCount: Int = {
    def getLineContiguousCount(line: Array[Long]): Int = {

      var i = 0
      var count = 0
      while (i < line.length) {

        var j = 0
        var prevUnoccupied = false

        while (j < boardSize) {
          val unoccupied = getBitAt(line, i, j) == 0
          if (unoccupied) {
            if (!prevUnoccupied)
              count += 1

            prevUnoccupied = true
          } else
            prevUnoccupied = false

          j += 1

        }

        i += 1
      }

      count
    }

    val rows = getLineContiguousCount(rowGrid)
    val cols = getLineContiguousCount(colGrid)

    rows + cols
  }

  def maxContiguousOpenLines: Int = {

    def getMaxContiguous(theGrid: Array[Long]): Int = {
      var i = 0
      var max = 0
      var currentMax = 0
      while (i < theGrid.length) {
        if (theGrid(i) == boardSizeInfo.anySizeEmptyLineValue) {
          currentMax += 1
          if (currentMax > max) { max = currentMax }
        } else {
          currentMax = 0
        }
        i += 1
      }
      max
    }

    val maxRows = getMaxContiguous(rowGrid)
    val maxCols = getMaxContiguous(colGrid)

    if (maxRows > maxCols) maxRows else maxCols

  }

  private def getFullLine(theGrid: Array[Long]): Array[Long] = {
    // it's a hack to pass back a sentinel value (-1)
    // but it prevents us from doing a splitAt (or just constructing this thing functionally
    // doing it this way is orders of magnitude faster
    val a = new Array[Long](boardSize)
    var i = 0
    var n = 0
    while (i < a.length) {
      if (theGrid(i) == boardSizeInfo.boardSizeFullLineValue) {
        a(n) = i
        n += 1
      }
      i += 1
    }

    // add our sentinel
    if (n < a.length)
      a(n) = -1

    a
  }

  def fullRows: Array[Long] = getFullLine(rowGrid)
  def fullCols: Array[Long] = getFullLine(colGrid)

  def occupied(row: Int, col: Int): Boolean = occupancyGrid(row)(col)

  private def getBitAt(theGrid: Array[Long], row: Int, col: Int): Long = (theGrid(row) >> col) & 1
  private def getBitAt(row: Int, col: Int): Long = getBitAt(rowGrid, row, col)

  private def setBitAt(theGrid: Array[Long], row: Int, col: Int) = theGrid(row) |= (1 << col)
  private def unSetBitAt(theGrid: Array[Long], row: Int, col: Int) = theGrid(row) &= ~(1 << col)

  /**
   * set occupancy to true at this position
   */
  def occupy(row: Int, col: Int): Unit = {
    setBitAt(rowGrid, row, col)
    setBitAt(colGrid, col, row)
    occupancyGrid(row)(col) = true
  }

  /**
   * set occupancy to false at this position
   */
  def unoccupy(row: Int, col: Int): Unit = {
    unSetBitAt(rowGrid, row, col)
    unSetBitAt(colGrid, col, row)
    occupancyGrid(row)(col) = false
  }

}

object OccupancyGrid {

  def fillerup: (Int) => Long = (size: Int) => math.pow(2, size).toLong - 1

  private def getNewGrid(rows: Int, cols: Int, fillMe: Boolean): Array[Long] =
    if (fillMe) Array.ofDim[Long](rows).map(_ => fillerup(cols)) else new Array[Long](rows)

  private def getNewOccupancyGrid(rows: Int, cols: Int, fillMe: Boolean): Array[Array[Boolean]] =
    if (fillMe) Array.fill(rows, cols)(true) else Array.ofDim[Boolean](rows, cols)

  def apply(rows: Int, cols: Int, filled: Boolean, boardSizeInfo: BoardSizeInfo): OccupancyGrid = OccupancyGrid(
    rows,
    cols,
    OccupancyGrid.getNewGrid(rows, cols, filled),
    OccupancyGrid.getNewGrid(rows, cols, filled),
    OccupancyGrid.getNewOccupancyGrid(rows, cols, filled),
    boardSizeInfo
  )

}