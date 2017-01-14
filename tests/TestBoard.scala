/**
 * Created by nathan on 1/11/17.
 * tests for Board objects
 */
import org.scalatest.{FlatSpec, _}

class TestBoard extends FlatSpec {

  trait BoardFixture {
    val boardSize = Board.BOARD_SIZE
    val board = new Board(boardSize)
    val pieces = new Pieces
    val initialOccupied: Int = board.occupiedCount
    val initialOpenLines: Int = board.grid.openLineCount

    def addRow(at: Int): Unit = {

      board.place(Pieces.h5line, Loc(at, 0))
      board.place(Pieces.h5line, Loc(at, 5))

    }

    def addCol(at: Int): Unit = {
      board.place(Pieces.v5line, Loc(0, at))
      board.place(Pieces.v5line, Loc(5, at))
    }
  }

  trait BoardCopyFixture extends BoardFixture {

    board.place(pieces.getRandomPiece, Loc(boardSize / 2, boardSize / 2)) // just place one in the middle

    val copy: Board = Board.copy("copy", board)

    val sourceColorGrid: Array[Array[Cell]] = board.colorGrid
    val sourceGrid: OccupancyGrid = board.grid
    val copyColorGrid: Array[Array[Cell]] = copy.colorGrid
    val copyGrid: OccupancyGrid = copy.grid
  }

  behavior of "A board"

  it must "be larger than the largest piece" in {
    (0 to 4) foreach { i =>
      intercept[IllegalArgumentException] { //noinspection ScalaUnusedSymbol
        val board = new Board(i)
      }
    }
    val board = new Board(5)
    assert(board.isInstanceOf[Board])
  }

  it must "make an exact copy" in {
    new BoardCopyFixture {

      for {
        i <- sourceColorGrid.indices
        j <- sourceColorGrid(i).indices
      } {

        assert(sourceColorGrid(i)(j).occupied == copyColorGrid(i)(j).occupied, "- color grids don't match") // ensure source and destination match
        assert(sourceGrid.occupied(i, j) == copyGrid.occupied(i, j), "- BitVector grids don't match") // ensure OccupancyGrid grids also match
        assert(sourceColorGrid(i)(j).occupied == copyGrid.occupied(i, j), "- Source color grid doesn't match copy BitVector grid") // ensure OccupancyGrid grids also matches source color grid

      }

    }
  }

  it must "not reflect changes in underlying board when changes are made on a copy" in {
    new BoardCopyFixture {
      // place the random piece at the beginning
      val piece: Piece = pieces.getRandomPiece
      board.place(piece, Loc(0, 0)) // we know that the source board is empty at (0,0) as it is not filled in on the fixture

      // iterate through piece indices as they will match the board at location (0,0)
      for {
        i <- piece.colorGrid.indices
        j <- piece.colorGrid(i).indices
        if piece.colorGrid(i)(j).occupied
      } {
        assert(sourceColorGrid(i)(j).occupied != copyColorGrid(i)(j).occupied, "- color grids shouldn't match")
        assert(sourceGrid.occupied(i, j) != copyGrid.occupied(i, j), "- OccupancyGrids shouldn't match")
        assert(sourceColorGrid(i)(j).occupied != copyGrid.occupied(i, j), "- Source color grid shouldn't match copy OccupancyGrid")
      }
    }
  }

  it must "reflect the correct score after placing pieces" in {
    new BoardFixture {
      var i = 0
      // note - this is quick and dirty and potentially fragile
      //        as there is no optimization that runs and if the pieces
      //        are returned in a different order, we may not
      //        clear enough lines to have this execute safely
      for (piece <- pieces.pieceList) {
        board.clearLines()
        val boardScore = board.occupiedCount
        val pieceScore = piece.pointValue
        val loc = board.legalPlacements(piece).head
        board.place(piece, loc)
        assert(board.occupiedCount === boardScore + pieceScore, "- " + piece.name + " - index:" + i)
        i += 1
      }
    }
  }

  it must "reduce open lines by board size + 1 after adding a row" in {
    new BoardFixture {
      addRow(0)
      assert(board.grid.openLineCount === (initialOpenLines - (boardSize + 1)))
      assert(board.occupiedCount == (initialOccupied + boardSize))
    }
  }

  it must "reduce open lines by board size + 1 after adding a col" in {
    new BoardFixture {
      addCol(0)
      assert(board.grid.openLineCount === (initialOpenLines - (boardSize + 1)))
      assert(board.occupiedCount == (initialOccupied + boardSize))
    }
  }

  it must "have no open lines after adding a row and a column (invalid state)" in {
    new BoardFixture {
      addCol(0)
      addRow(0)
      assert(initialOpenLines - (boardSize * 2) === 0)
      assert(board.occupiedCount === (initialOccupied + boardSize + boardSize - 1))
    }
  }

  it must "have same number of open lines after adding and clearing the same row" in {
    new BoardFixture {

      addRow(boardSize / 2)
      board.clearLines()
      assert(board.grid.openLineCount === initialOpenLines)

    }
  }

  it must "have same number of open lines after adding and clearing the same column" in {
    new BoardFixture {
      addCol(boardSize / 2)
      board.clearLines()
      assert(board.grid.openLineCount === initialOpenLines)

    }
  }

  it must "have the same number of open lines after adding and clearing a row and a column" in {
    new BoardFixture {
      addCol(boardSize / 2)
      addRow(boardSize / 2)
      board.clearLines()
      assert(board.grid.openLineCount === initialOpenLines)

    }
  }

  it must "clear four full rows and four full columns" in {
    new BoardFixture {
      (0 to 3).foreach(i => addRow(i))
      assert(board.occupiedCount === (4 * boardSize))
      assert(board.clearLines()._1 === 4)
      assert(board.occupiedCount === 0)

      (0 to 3).foreach(i => addCol(i))
      assert(board.occupiedCount === (4 * boardSize))
      assert(board.clearLines()._2 === 4)
      assert(board.occupiedCount === 0)

    }
  }

  it must "find the correct contiguous lines" in {
    new BoardFixture {

      0 until boardSize foreach { i =>
        val expected = { if (i < boardSize / 2) boardSize - (i + 1) else i }

        addRow(i)
        val rowMax = board.grid.maxContiguousOpenLines
        assert(expected === rowMax)
        board.clearLines()

        addCol(i)
        val colMax = board.grid.maxContiguousOpenLines
        assert(expected === colMax)
        board.clearLines()

      }
      /*
      0 - 9
      1 - 8
      2 - 7
      3 - 6
      4 - 5
      5 - 5
      6 - 6
      7 - 7
      8 - 8
      9 - 9
      */
    }

  }

  it must "have the same occupancy after clearing a row" in {
    new BoardFixture {
      addRow(0)
      board.clearLines()
      for {
        i <- board.colorGrid.indices
        j <- board.colorGrid(0).indices
      } assert(board.colorGrid(i)(j).occupied === board.grid.occupied(i, j))
    }
  }

  it must "count legal placements for a piece correctly" in {
    def expected(piece: Piece): Int = {
      val boardSize = Board.BOARD_SIZE
      (boardSize - piece.rows + 1) * (boardSize - piece.cols + 1)
    }

    val pieces = new Pieces
    for {
      piece <- pieces.pieceList
      board = new Board(Board.BOARD_SIZE)
    } {
      val legal = board.legalPlacements(piece).length
      assert(expected(piece) === legal)
    }
  }

  // todo create specification and pass it into a game - this will allow command line parameterization of specifications
  // todo test that specifications of all combinations and permutations are actually invoked correctly

}