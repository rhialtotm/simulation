/**
 * Created by nathan on 12/10/16.
 * Game will hold a reference to the current board and will also invoke simulations
 *
 * Todo: save every move in a game so you can replay it if it's awesome
 *
 * Todo: Maximizer on first round should still be correct even if simulations are bypassed
 *       Simulation skip message can be output only once, also.  don't even call piece simulation
 *
 * Todo: If occupied is minimized and islands are maximized, then the next should be keep
 *       rows and columns clear - the most number...!!!  This could be the winner.
 *
 * Todo: It doesn't seem as if doing the maximum number of simulations really buys you much
 *       so create an ability tune the simulation count to a number that results in the rolling average
 *       (sliding window) of simulations per second from the previous 12? runs to be less than 1.5 seconds
 *       per permutation.  If you can do more, great.  If you can't, that's great also.
 *       potentially you could use the concurrency framework - that would be cool
 *
 */
import GameUtil._

object GameOver extends Exception

class Game(val highScore: Long) {

  import scala.collection.mutable.ListBuffer

  private val SLOW_COMPUTER = true

  // TODO: the math right now says we will never be in long territory so switch this bad boy to an int
  private val MAX_SIMULATION_ITERATIONS = if (SLOW_COMPUTER) 30000l else 1000000l //  100l - 100 speeds up the game significantly

  private val BYATCH_THRESHOLD = 55000 // your system has some cred if it is doing more than this number of simulations / second

  private val CONTINUOUS_MODE = true // set to false to have the user advance through each board placement by hitting enter

  private val board: Board = new Board(10)
  private val gamePieces: Pieces = new Pieces

  //Todo: create a counter class that has the ability to increment itself
  //      it can maintain it's own internal representation of things to count
  //      you can get the current count just by asking for it - right now we're asking the longIter (buffered) for it's .head
  //      you can make the code a lot more clear by just asking this counter class for the current count and hide
  //      the Iterator used to maintain the count

  private val score: BufferedIterator[Long] = longIter.buffered
  private val rowsCleared: BufferedIterator[Long] = longIter.buffered
  private val colsCleared: BufferedIterator[Long] = longIter.buffered
  private val rounds: BufferedIterator[Long] = longIter.buffered
  private val placed: BufferedIterator[Long] = longIter.buffered
  private def incrementCounter(count: Int, it: Iterator[Long]): Unit = for (i <- 0 until count) it.next

  private val simulationsPerSecond = new ListBuffer[Long]

  def run(): (Long, Long, Long) = {

    // val t1 = System.currentTimeMillis()
    val duration = new Timer

    try {

      do {

        // get 3 random pieces
        val pieces = getPieces

        // show the pieces in the order they were randomly chosen
        showPieces(pieces)

        // set up a test of running through all orderings of piece placement (permutations)
        // and for each ordering, try all combinations of legal locations
        // the lowest board score after trying all legal locations will what is chosen
        // right now this is kind of stupid and the game doesn't play very well...but we can make it better
        val permutations = pieces
          .permutations
          .toList
          // sort by the occupied count, then max by the number of places that can accept a 3x3 box
          .map(pieceSequenceSimulation(_, MAX_SIMULATION_ITERATIONS)).sorted

        val best = permutations.head

        println(getSimulationResultsString("     Chosen: " + piecesToString(best.pieceLocation.map(pieceLoc => pieceLoc._1)), best, None))

        // pause for effect
        Thread.sleep(2000)

        best.pieceLocation.foreach(tup => handleThePiece(best, tup._1, tup._2, board.placeOrFail))

        showBoardFooter()

      } while (CONTINUOUS_MODE || (!CONTINUOUS_MODE && (Console.in.read != 'q')))

    } catch {

      case GameOver => // normal game over
      case e: Throwable =>
        println("abnormal run termination:\n" + e.toString)
        // todo: find out what type of error assert is throwing and match it
        throw new IllegalStateException()

    }

    showGameOver(duration)

    // return the score and the number of rounds to Main - where such things are tracked across game instances
    // Todo:  Maybe a GameRunner class should be introduced so that Main is simply a hand off to GameRunner
    // this would be more clear - then Main's only purpose is to be the application entry point
    (score.head, rounds.head, simulationsPerSecond.max)

  }

  // use this method to return specific pieces under specific circumstances
  // under normal conditions if (false
  // just return a random set of 3 pieces
  private def getPieces: List[Piece] = {

    val pieces = {
      // this code provides specific pieces for the (probable) last iteration
      // was used as a condition to debug a situation in the end game
      // if (board.occupiedCount > 50)

      // used to test a few rounds...
      /* if (rounds.head ==1)
        Piece.getNamedPieces("VerticalLine5", "LowerLeftEl", "HorizontalLine3")
      else if (rounds.head == 2)
        Piece.getNamedPieces("Box", "VerticalLine3", "BigUpperRightEl")
      else if (rounds.head == 3)
       Piece.getNamedPieces("BigUpperLeftEl", "BigBox", "VerticalLine5")
      else*/
      List.fill(3)(gamePieces.getRandomPiece)

    }
    pieces
  }

  case class Simulation(
    boardCount: Int,
    openLines: Int,
    islands: Map[Int, Int],
    maximizerCount: Int,
    entropy: Double,
    pieceLocation: List[(Piece, Option[(Int, Int)])],
    board: Board
  ) extends Ordered[Simulation] {

    val islandMax: Int = islands.keys.max
    import scala.math.Ordered.orderingToOrdered

    // new algo - lowest boardCount followed by largest island
    // the following provides tuple ordering to ordered to make the tuple comparison work
    def compare(that: Simulation): Int = {
      (this.boardCount, that.openLines, that.islandMax, that.maximizerCount, this.entropy)
        .compare(that.boardCount, this.openLines, this.islandMax, this.maximizerCount, that.entropy)
    }

  }

  private def pieceSequenceSimulation(pieces: List[Piece], maxIterations: Long): Simulation = {

    val t1 = System.currentTimeMillis()
    val simulations = longIter.buffered

    val p1 = pieces.head
    val p2 = pieces(1)
    val p3 = pieces(2)

    def placeMe(piece: Piece, theBoard: Board, loc: (Int, Int)): Board = {
      simulations.next() // simulation counter increased
      val boardCopy = copyBoard(List(piece), theBoard)
      boardCopy.simulatePlacement(piece, loc)
      boardCopy
    }

    def maximizerLength(theBoard: Board): Int = theBoard.legalPlacements(Game.maximizer).length

    // todo: make this recursive...
    def createSimulations: List[Simulation] = {

      val listBuffer1 = new ListBuffer[Simulation]
      val listBuffer2 = new ListBuffer[Simulation]
      val listBuffer3 = new ListBuffer[Simulation]

      for (loc1 <- this.board.legalPlacements(p1).par) {
        if (simulations.head < maxIterations) {

          val board1Copy = placeMe(p1, this.board, loc1)
          val maximizerLength1 = maximizerLength(board1Copy)
          val simulation1 = Simulation(board1Copy.occupiedCount, board1Copy.openLines, board1Copy.islands, maximizerLength1, board1Copy.entropy, List((p1, Some(loc1)), (p2, None), (p3, None)), board1Copy)
          synchronized { listBuffer1 append simulation1 }

          for (loc2 <- board1Copy.legalPlacements(p2).par) {
            if (simulations.head < maxIterations) {

              val board2Copy = placeMe(p2, board1Copy, loc2)
              val maximizerLength2 = maximizerLength(board2Copy)
              val simulation2 = Simulation(board2Copy.occupiedCount, board2Copy.openLines, board2Copy.islands, maximizerLength2, board2Copy.entropy, List((p1, Some(loc1)), (p2, Some(loc2)), (p3, None)), board2Copy)

              synchronized { listBuffer2 append simulation2 }

              for (loc3 <- board2Copy.legalPlacements(p3).par) {
                if (simulations.head < maxIterations) {

                  val board3Copy = placeMe(p3, board2Copy, loc3)
                  val maximizerLength3 = maximizerLength(board3Copy)
                  val simulation3 = Simulation(board3Copy.occupiedCount, board3Copy.openLines, board3Copy.islands, maximizerLength3, board3Copy.entropy, List((p1, Some(loc1)), (p2, Some(loc2)), (p3, Some(loc3))), board3Copy)

                  synchronized { listBuffer3 append simulation3 }

                }
              }
            }
          }
        }
      }

      // if we have a 3 piece solution we should use it as two piece and one piece solutions mean GameOver
      // at least along this particular simulation path
      if (listBuffer3.nonEmpty)
        listBuffer3.toList
      else if (listBuffer2.nonEmpty)
        listBuffer2.toList
      else if (listBuffer1.nonEmpty)
        listBuffer1.toList
      else
        // arbitrary large number so that this option will never wih against
        // options that are still viable
        List(Simulation(100000, this.board.openLines, this.board.islands, 0, this.board.entropy, List((p1, None), (p2, None), (p3, None)), this.board))

    }

    val options = createSimulations
    val best = options.sorted.head

    // invert the default comparison and take that as the result for worst
    val worst = options.sortWith(_ > _).head

    val t2 = System.currentTimeMillis
    // todo: create a timer class that starts, stops and does a toString
    val duration = t2 - t1

    val largeValuesFormat = "%,5d"

    val simulationCount = largeValuesFormat.format(simulations.head)

    val perSecond = if (duration > 0) (simulations.head * 1000) / duration else 0

    simulationsPerSecond append perSecond

    val durationString = largeValuesFormat.format(duration) + "ms"
    val sPerSecond = largeValuesFormat.format(perSecond)

    val prefix = "permutation: " + piecesToString(pieces)

    println(
      getSimulationResultsString(prefix, best, Some(worst))
        + " - simulations: " + simulationCount + " in " + durationString
        + " (" + sPerSecond + "/second" + (if (perSecond > BYATCH_THRESHOLD) " b-yatch" else "") + ")"
    )

    best

  }

  private def getSimulationResultsString(prefix: String, best: Simulation, worst: Option[Simulation] = None): String = {

    val greenify = (b: Boolean, v: AnyVal, valFormat: String, label: String, labelFormat: String) => {
      val result = valFormat.format(v)
      labelFormat.format(label) + (if (b) (GameUtil.GREEN + result + GameUtil.SANE) else result)
    }

    val openFormat = "%2d"
    val parenFormat = " (" + openFormat + ")"
    val parenEntropyFormat = " (" + entropyFormat + ")"
    val labelFormat = " %s: "
    val longLabelFormat = "     " + labelFormat

    val results = (best, worst) match {
      case (b: Simulation, w: Some[Simulation]) => {
        (
          greenify((b.boardCount < w.get.boardCount), b.boardCount, openFormat, "occupied", labelFormat)
          + greenify(false, w.get.boardCount, parenFormat, "", "")

          + greenify((b.openLines > w.get.openLines), b.openLines, openFormat, "open lines", labelFormat)
          + greenify(false, w.get.openLines, parenFormat, "", "")

          + greenify(b.islandMax > w.get.islandMax, b.islandMax, openFormat, "islandMax", labelFormat)
          + greenify(false, w.get.islandMax, parenFormat, "", "")

          + greenify(b.maximizerCount > w.get.maximizerCount, b.maximizerCount, openFormat, "maximizer", labelFormat)
          + greenify(false, w.get.maximizerCount, parenFormat, "", "")

          + greenify(b.entropy < w.get.entropy, b.entropy, entropyFormat, "entropy", labelFormat)
          + greenify(false, w.get.entropy, parenEntropyFormat, "", "")
        )
      }
      case (b: Simulation, None) => {
        (
          greenify(true, b.boardCount, openFormat, "occupied", labelFormat)
          + greenify(true, b.openLines, openFormat, "open lines", longLabelFormat)
          + greenify(true, b.islandMax, openFormat, "islandMax", longLabelFormat)
          + greenify(true, b.maximizerCount, openFormat, "maximizer", longLabelFormat)
          + greenify(true, b.entropy, entropyFormat, "entropy", longLabelFormat)
        )
      }

    }

    prefix + " -" + results

  }

  // todo:  can this be turned into an implicit for a List[Piece].toString call?  if so, that would be nice
  private def piecesToString(pieces: List[Piece]): String = pieces.map(_.name).mkString(", ")

  private def copyBoard(pieces: List[Piece], aBoard: Board): Board = Board.copy("board: " + pieces.map(_.name).mkString(", "), aBoard)

  private def handleThePiece(best: Simulation, piece: Piece, loc: Option[(Int, Int)], f: (Piece, Option[(Int, Int)]) => Unit): Unit = {

    val currentOccupied = board.occupiedCount
    val curRowsCleared = rowsCleared.head
    val curColsCleared = colsCleared.head

    println("\nAttempting piece: " + ((placed.next % 3) + 1) + "\n" + piece.toString + "\n")

    // a function designed to throw GameOver if it receives no valid locations
    f(piece, loc)

    piece.usage.next

    incrementCounter(piece.pointValue, score)

    // placing a piece puts underlines on it to highlight it
    println(board)

    // so now clear any previously underlined cells for the next go around
    clearPieceUnderlines()

    handleLineClearing()

    val rowsClearedThisRun = rowsCleared.head - curRowsCleared
    val colsClearedThisRun = colsCleared.head - curColsCleared
    val positionsCleared = ((rowsClearedThisRun + colsClearedThisRun) * 10) - (rowsClearedThisRun * colsClearedThisRun)

    val expectedOccupiedCount = currentOccupied + piece.pointValue - positionsCleared

    assert(expectedOccupiedCount == board.occupiedCount, "Expected occupied: " + expectedOccupiedCount + " actual occupied: " + board.occupiedCount)

    println("Score: " + getScoreString(score.head) + " (" + getScoreString(highScore) + ")"
      + " - occupied: " + board.occupiedCount
      + " - open lines: " + board.openLines
      + " - largest contiguous unoccupied: " + best.islandMax // a little unusual - todo: you could put islandMax on the board - makes more sense there
      + " - maximizer positions available: " + board.legalPlacements(Game.maximizer).length
      + " - disorder (aka entropy): " + entropyFormat.format(board.entropy))
  }

  private def handleLineClearing() = {

    val result = board.clearLines()

    if (result._1 > 0 || result._2 > 0) {

      def printMe(i: Int, s: String): Unit = if (i > 0) println("cleared " + i + " " + s + (if (i > 1) "s" else ""))

      printMe(result._1, "row")
      printMe(result._2, "column")

      // show an updated board reflecting the cleared lines
      println("\n" + board)

      incrementCounter(result._1, rowsCleared)
      incrementCounter(result._2, colsCleared)

    }

    incrementCounter((result._1 + result._2) * board.layout.length, score)
  }

  private def clearPieceUnderlines() = {
    // Todo: as right now clearUnderlines just loops through the whole board which is between 91 to 99 operations to many
    // if you passed it the piece that just got underlined, you could just remove underlines where that piece was placed
    //   also this is probably best a Board operation not a Game operation
    for {
      row <- board.layout
      cell <- row
      if cell.underline
    } cell.underline = false
  }

  private def showGameOver(duration: Timer) = {

    println

    println(gamePieces.toString)

    println("\nGAME OVER!!\n")

    println(labelFormat.format("Final Score") + getScoreString(score.head))
    println(labelNumberFormat.format("Rounds", rounds.head))
    println(labelNumberFormat.format("Rows Cleared", rowsCleared.head))
    println(labelNumberFormat.format("Cols Cleared", colsCleared.head))
    println(duration.toString) // toString stops the timer
  }

  private def showBoardFooter() = {
    val bestPerSecond = simulationsPerSecond.max

    println("\nAfter " + "%,d".format(rounds.head) + " rounds"
      + " - rows cleared: " + "%,d".format(rowsCleared.head)
      + " - columns cleared: " + "%,d".format(colsCleared.head)
      + " - best simulations per second: " + "%,d".format(bestPerSecond))

    if (!CONTINUOUS_MODE)
      println("type enter to place another piece and 'q' to quit")
  }

  private def showPieces(pieces: List[Piece]): Unit = {

    // we'll need the height of the tallest piece as a guard for shorter pieces where we need to
    // print out spaces as placeholders.  otherwise array access in the for would be broken
    // if we did some magic to append fill rows to the pieces as strings array...
    val tallestPieceHeight = pieces.map(piece => piece.rows).reduceLeft((a, b) => if (a > b) a else b)

    // because we're not printing out one piece, but three across, we need to split
    // the toString from each piece into an Array.  In this case, we'll create a List[Array[String]]
    val piecesToStrings = pieces map { piece =>

      val a = piece.toString.split('\n')
      if (a.length < tallestPieceHeight)
        a ++ Array.fill(tallestPieceHeight - a.length)(piece.printFillString) // fill out the array
      else
        a // just return the array

    }

    println("\nRound: " + (rounds.next + 1) + " - Candidate pieces:")

    // turn arrays into a list so you can transpose them
    // transpose will create a list of 1st rows, a list of 2nd rows, etc.
    // then print them out - across and then newline delimit
    piecesToStrings.map(a => a.toList)
      .transpose
      .foreach { l => print(l.mkString); println }

  }

}

object Game {

  // one of the optimizations is to ensure that the maximum number of
  // maximum pieces can fit on a board from all the boards simulated in the permutation of a set of pieces
  protected val maximizer = new Box("Maximizer", GameUtil.CYAN, 3)

  def showGameStart(): Unit = {

    println("GAME START")
    println("\nThis game works by first selecting 3 pieces from the set of all possible pieces.")
    println("Then it will try all possible orderings (permutations) of placements of those 3 pieces")
    println("(up to six permutations will be selected depending on whether there are any duplicates).")
    println("\nThen for each permutation, it will try each position on the board for the first piece")
    println("and will determine the outcome of clearing lines for that piece piece, then it will place")
    println("the second piece at all possible locations and check the outcome of clearing lines for the second")
    println("and finally it will check the third piece at all possible locations and check the outcome")
    println("of clearing lines for this last piece.")
    println("\nEach of these combinations of placements will then store the maximum number of legal positions")
    println("available for this piece (called the maximizer):\n")
    println(maximizer.toString)
    println("\nThe game uses the maximizer because it generally is a good choice for making sure there is")
    println("plenty of space available.")
    println("\nThe combination of piece placements with the least number of board positions occupied and the most")
    println("number of legal positions for the maximizer piece is the one that will be selected to play.")
    println("Each combination for each permutation is called a 'simulation'.")

  }
}
