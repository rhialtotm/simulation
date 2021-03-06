import scala.collection.GenSeq

/**
 * Created by nathan on 12/9/16.
 *
 * Board is the game Board plus helper functions.
 * It's like other pieces in that it has a name, and a layout and a color (which is the color when first instantiated)
 *
 * Boards can be created at will in order to test simulations
 * There will always be the main board held by the Game but other Boards will be created when running simulations.
 * rather a lot of them, I'd say.
 *
 */
// primary constructor mostly called on copy. alternate is called once - for the board used in the game

case class ClearedLines(rows: Int, cols: Int)

class Board(
  final val grid:      OccupancyGrid,
  final val colorGrid: Array[Array[String]],
  final val context:   Context
) {

  def this(context: Context) {
    // initial board creation just requires a size - initialize with all proper defaults
    this(
      OccupancyGrid(context.boardSize, context.boardSize, filled = false, context.boardSizeInfo),
      Board.getBoardColorGrid(context.boardSize),
      context
    )
  }

  final val specification: Specification = context.specification

  final val boardSize: Int = context.boardSize

  final val cachedOccupancyGrid: Array[Array[Boolean]] = grid.getOccupancyGrid

  // def so a new one is created every time boardScore is called
  def boardScore: BoardScore = BoardScore(this, context)

  def show: String = {

    val s = new StringBuilder()

    for {
      row <- grid.occupancyGrid.indices
      col <- grid.occupancyGrid(row).indices
    } {
      val box = {

        val occupied = cachedOccupancyGrid(row)(col)
        val color = colorGrid(row)(col)

        if (occupied)
          color + Board.BOX_CHAR + StringFormats.SANE
        else
          Board.UNOCCUPIED_BOX_CHAR

      }
      val nl = if (col == grid.occupancyGrid(0).length - 1) " \n" else ""
      s ++= box + " " + nl
    }

    // we don't need the final newline
    s.toString.dropRight(1)
  }

  def maximizerCount: Int = legalPlacements(context.maximizer3x3).length

  def avoidMiddleSum: Int = {
    var i = 0
    var j = 0
    var sum = 0
    val a = this.specification.avoidMiddleArray
    while (i < a.length) {
      while (j < a.length) {
        if (cachedOccupancyGrid(i)(j))
          sum += a(i)(j)
        j += 1
      }
      j = 0
      i += 1
    }
    sum
  }

  // allow for setting this by the game which does the scoring
  var roundScore = 0

  // changed to not use a rotated copy of the board
  // slight increase in LOC but definite decrease in % of code execution time
  // from ~24% with the original version - now it's 226102 / 1543684 = 0.146469096 = 14.6% of code execution time
  //
  // next level of optimization.
  // start: clearLines takes 58% of placeMe at 2,992 per second in the profiler
  // fullRow and fullCol eliminate forall:  now 35% of placeMe at 5,344 per second in the profiler
  // fullRows and fullCols built with tail recursion: now 4% of placeMe at 89,635 / second in the profiler
  // final  removed foreach on clearableRows and clearableCols:  3% of placeMe at 104,000 / second in the profiler
  // overall improvement = 3300%
  //
  // at start of this optimization, clearLines was 11% of overall execution time at end, it was .3%
  // this is a super strong argument for using while loops and buildings things tail recursively when performance
  // is on the line in tight loops
  //
  // much further down the road - sped this up by 180% by removing lambdas,
  // optimizing the sentinel creation in calls to grid.fullRows, grid.fulLCols
  def clearLines: ClearedLines = {

    val clearableRows = grid.fullRows
    val clearableCols = grid.fullCols

    // 57% speed up of this method by getting rid of the lambdas
    // used to eliminate separate clearRows/clearCols
    // shee-ite

    def clearLines(linesToClear: Array[Long], rows: Boolean): Int = {
      var i = 0
      while (i < linesToClear.length && linesToClear(i) > -1) {
        val n = linesToClear(i).toInt

        rows match {
          case _ if rows => grid.clearRow(n)
          case _         => grid.clearCol(n)
        }

        i += 1
      }
      i
    }

    val clearedRows = clearLines(clearableRows, rows = true)
    val clearedCols = clearLines(clearableCols, rows = false)

    // rows cleared and cols cleared
    //(clearableRows.length, clearableCols.length)
    ClearedLines(clearedRows, clearedCols)
  }

  // start optimization
  // Execution Time: 26%, 5,212/s
  //
  // eliminate for comprehension version / replace with tail recur
  // Execution Time: 2%, 108,793/s - 1900% speedup
  def place(piece: Piece, loc: Loc, updateColor: Boolean): Unit = {

    val locRow = loc.row
    val locCol = loc.col

    val pieceRows = piece.rows
    val pieceCols = piece.cols
    val pieceGrid = piece.cachedOccupancyGrid
    val replaceColor = piece.color

    def checkCell(row: Int, col: Int): Unit = {
      if (pieceGrid(row)(col)) {

        // it turns out that initializing like this
        // val (i, j) = (row + locRow, col + locCol)
        // took 3% of execution time.  christ.
        val i = row + locRow
        val j = col + locCol

        if (updateColor) this.colorGrid(i)(j) = replaceColor
        this.grid.occupy(i, j)

      }
    }

    var r = 0
    var c = 0
    while (r < pieceRows) {
      while (c < pieceCols) {
        checkCell(r, c)
        c += 1
      }

      c = 0
      r += 1
    }

  }

  // optimize this
  // at start (% is of total execution time)
  // Execution Time: 15%, 2,125/s
  //
  // first remove the toList which was required by using allLocations
  // added a lazyVal on board to get an allLocations list (which is only computed on first use)
  // we'll see what this thing looks like with toList gone
  //
  // Execution Time: 14%, 2,281/s
  //
  // now add tail recursion creation of legalPlacement locations to get rid of the
  // current for comprehension used to build the list of legals
  //
  // Execution Time: 1%, 59,904/s - a 2500% increase of this section
  //
  // now that so many other things have been optimized, legalPlacements
  // takes ~12% (Own Time) out of all execution time
  // i don't see how this could be optimized any further

  def legalPlacements(piece: Piece): Array[Loc] = {
    // walk through each position on the board
    // see if the piece fits at that position, if it does, add that position to the list
    /* idiomatic == slow
    for { loc <- Board.allLocationsList.par if legalPlacement(piece, loc) } yield loc */

    // moving to array and cleaning up lazy vals in Piece
    // made this thing scream - 10x faster
    var i = 0
    var n = 0

    val locs = context.allLocations
    val buf = new Array[Loc](locs.length)

    while (i < locs.length) {
      val loc = locs(i)
      if (legalPlacement(piece, loc)) {
        buf(n) = loc

        n += 1
      }
      i += 1
    }

    // now 30% of the time is spent just doing a splitAt - maybe we can remove...
    // removed buf.splitAt by just copying buf to a new resultBuf array
    // went from 14,955/s to 21,692/s on macbook air
    // 45% faster!
    val resultBuf = new Array[Loc](n)
    Array.copy(buf, 0, resultBuf, 0, n)
    resultBuf

  }

  private[this] def legalPlacement(piece: Piece, loc: Loc): Boolean = {

    // 607K/s with the returns inline vs. 509K/s with the returns removed.  the returns stay
    val locRow = loc.row
    val locCol = loc.col
    val pieceRows = piece.rows
    val pieceCols = piece.cols

    if ((pieceRows + locRow) > boardSize) return false // exceeds the bounds of the board - no point in checking any further
    if ((pieceCols + locCol) > boardSize) return false // exceeds the bounds of the board - no point in checking any further

    val pieceGrid = piece.cachedOccupancyGrid

    // find all instances
    // where the piece has an occupied value and the board has an occupied value - that is illegal, so bail
    // otherwise it's legal

    // using a while loop rather than a for comprehension because
    // the while loop is a LOT faster
    var r = 0
    var c = 0
    while (r < pieceRows) {
      while (c < pieceCols) {
        if ( grid.occupancyGrid(r + locRow)(c + locCol) && pieceGrid(r)(c)) {
          return false
        }
        c += 1
      }
      c = 0
      r += 1
    }

    // if we didn't bail, then this piece placement is legal
    true

  }

  def countNeighbors(locs: Array[Loc]): Array[Int] = {

    val counts = Array(0, 0, 0, 0, 0)
    val locLength = locs.length

    // walk through all directions
    def countLocationNeighbor(loc: Loc, locNeighbors: Array[Loc]): Unit = {

      val length = locNeighbors.length
      var i = 0
      var count = 0
      while (i < length) {
        val tryLoc = locNeighbors(i)

        val row = tryLoc.row
        val col = tryLoc.col

        // if it's out of bounds or the neighbor is on...
        // moving this boolean test inline sped up countLocationNeighbor dramatically by, 20x
        // wtf
        // given other tests with YourKit, I'm not sure that it's reporting this correctly.  it may be that it's a heisenberg issue
        if (row == boardSize || col == boardSize || row == -1 || col == -1 || cachedOccupancyGrid(row)(col)) { count += 1 }

        i += 1
      }

      counts(count) += 1

    }

    def countAllLocationNeighbors(): Unit = {
      var i = 0
      while (i < locLength) {
        val loc = locs(i)
        if (cachedOccupancyGrid(loc.row)(loc.col)) // don't count occupied
          ()
        else {

          countLocationNeighbor(loc, context.allLocationNeighbors(i))

        }

        i += 1
      }
    }

    countAllLocationNeighbors()

    counts

  }

}

object Board {

  val BOARD_COLOR = StringFormats.BRIGHT_BLACK
  val BOX_CHAR: String = /*"\u25A0"*/ "\u25A9" + StringFormats.SANE

  private val UNOCCUPIED_BOX_CHAR = BOARD_COLOR + BOX_CHAR

  def copy(boardToCopy: Board): Board = new Board(boardToCopy.grid.copy, boardToCopy.colorGrid, boardToCopy.context)

  private def getBoardColorGrid(size: Int): Array[Array[String]] = Array.tabulate(size, size) { (_, _) => ""
  }

}
