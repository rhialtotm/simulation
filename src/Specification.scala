/**
 * Created by nathan mccoy on  1/15/2017.
 *
 * introducing the Specification in order to configure it on the context
 *
 *  so that tests can be run to ensure that all specification combinations
 *   (from 1 to Specification.length) and then the permutations of those
 *   combinations all result in valid games that can play
 *   This is a general test that is useful
 *
 *   Also, this paves the way to run the game by trying all of the above
 *   n times to see which combination/permutation of specifications
 *   results in the highest scores...
 */

import scala.collection.JavaConverters._

import Implicits._
import Specification._
import scala.collection.immutable.{Iterable, ListMap}

case class Specification(spec: ListMap[String, OptimizationFactor]) {

  val length: Int = spec.size

  //def apply(i: Int): OptimizationFactor = weightedSpec(i)
  def apply(s: String): OptimizationFactor = spec(s)

  // provided so that the getNamedResult function in boardScore can iterate over a while loop
  // as using named keys into the ListMap along with apply() access is VERY SLOW
  val optimizationFactors: Array[OptimizationFactor] = spec.values.toArray

  // initialize named optimization factors
  private def getOptimizationFactor(name: String): OptimizationFactor = {
    // a filtered spec is passed in - non non-enabled specifications are allowed
    // because of this, the direct values we provide on this Specification instance
    // need to provide a default for non-enabled values
    if (spec.contains(name))
      spec(name)
    else
      OptimizationFactor(enabled = false, name, minimize = false, 0.0, 0, 0, "", "")
  }

  val allMaximizersOptFactor: OptimizationFactor = getOptimizationFactor(Specification.allMaximizersKey)
  val avoidMiddleOptFactor: OptimizationFactor = getOptimizationFactor(Specification.avoidMiddleKey)
  val fourNeighborsOptFactor: OptimizationFactor = getOptimizationFactor(Specification.neighborsFourKey)
  val lineContiguousOptFactor: OptimizationFactor = getOptimizationFactor(Specification.lineContiguousUnoccupiedKey)
  val maxContiguousLinesOptFactor: OptimizationFactor = getOptimizationFactor(Specification.maxContiguousKey)
  val maximizerOptFactor: OptimizationFactor = getOptimizationFactor(Specification.maximizerKey)
  val occupiedOptFactor: OptimizationFactor = getOptimizationFactor(Specification.occupiedKey)
  val openLinesOptFactor: OptimizationFactor = getOptimizationFactor(Specification.openLinesKey)
  val threeNeighborOptFactor: OptimizationFactor = getOptimizationFactor(Specification.neighborsThreeKey)
  val twoNeighborsOptFactor: OptimizationFactor = getOptimizationFactor(Specification.neighborsTwoKey)

  def getOptimizationFactorExplanations: String = {
    // used by showGameStart
    spec.map(optFactor => "* " + optFactor._2.label + " - " + optFactor._2.explanation).mkString("\n")
  }

  private[this] def greenifyResult(optLabel: Option[String], isGreen: Boolean, value: Int): String = {
    // maximized results are negated to work with Simulation.compare so
    // as not to have to jump through hoops in that method
    // use math.abs to show them to the user in a way that makes sense

    val result = value.abs
    val resultString = optLabel match {
      case s: Some[String] => s.get.optFactorLabel + (if (isGreen) result.greenLabel else result.redLabel)
      case _               => (if (isGreen) result.greenLabel else result.redLabel).parens
    }

    resultString
  }

  def getSimulationResultsString(simulationResults: List[SimulationInfo], chosen: Simulation, showWorst: Boolean, bullShit: String): String = {
    // todo - add in simulations, skipped simulations, persecond information
    // todo - allow for outputting worst
    // todo - generate json for both brendan and lior

    def getPrefixString(name: String, value: String) = name.leftAlignedPadded(maxOptFactorLabelLength + 1).addColon +
      value.rightAlignedPadded(prefixWeightFormatLength) + spaceAndVertical

    val wrappedBullShit = bullShit.wrap(piecePrefixLength, GamePieces.tallestPiece + 1, StringFormats.BRIGHT_MAGENTA)

    val piecesString = simulationResults
      .zipWithIndex
      .map { case (result, index) => result.pieces.permutationPiecesHeader(index) }
      .spreadHorizontal(startAt = 0, bracketWith = StringFormats.VERTICAL_LINE + " ", separator = (StringFormats.VERTICAL_LINE + " "))

    val piecesHeader = wrappedBullShit.splice(piecesString.split("\n")) + "\n"

    // get the scores from the simulationInfo
    // transpose them to get all the same score values on the same row
    val scores: List[List[ScoreComponent]] = simulationResults.map(info => info.best.board.boardScore.scores).transpose

    val horizontal = StringFormats.HORIZONTAL_LINE.repeat(piecePrefixLength) +
      (StringFormats.CROSS_PIECE + StringFormats.HORIZONTAL_LINE.repeat(columnHeader.length - 1)).repeat(simulationResults.length) +
      StringFormats.VERTICAL_AND_LEFT + "\n"

    val scoreHeader = getPrefixString("score factor", "weight") + columnHeader.repeat(simulationResults.length) + "\n"

    val scoreString = scores.map(optScores =>
      getPrefixString(optScores.head.label, optScores.head.weight.weightLabel) +

        optScores.map(score =>
          " " + score.intValue.optFactorLabel.rightAlignedPadded(columnPadding) +
          score.normalizedValue.label(2).rightAlignedPadded(columnPadding + 3)
          + weightBuffer + score.weightedValue.weightLabel)

        .mkString(" " + StringFormats.VERTICAL_LINE)).mkString(" " + StringFormats.VERTICAL_LINE + "\n") + spaceAndVertical + "\n"

    // val winner = simulationResults.map(_.best.weightedSum).max

    val weightedSumString = "sum".leftAlignedPadded(maxOptFactorLabelLength + 1).addColon +
      scores.map(optScores => optScores.head.weight).sum.weightLabel.rightAlignedPadded(prefixWeightFormatLength) + spaceAndVertical +
      simulationResults.map { result =>

        val winner = result.best == chosen

        val sum = result.best.board.boardScore.scores.map(scoreComponent => scoreComponent.weightedValue).sum.weightLabel
        val sumWithLabel = (if (winner) "winner: " + sum else sum).rightAlignedPadded(permutationColumnWidth)
        if (winner) sumWithLabel.green else sumWithLabel.yellow

      }.mkString(spaceAndVertical) + spaceAndVertical

    piecesHeader  + horizontal + scoreHeader + horizontal + scoreString  + horizontal + weightedSumString
  }

  def getBoardResultString(boardResult: Array[Int]): String = {

    // this is called from a board placement result during the actual placing of pieces post-simulation
    // we keep board placement results separate on the one board that the whole game runs on
    // so that we can compare expected results from a simulation with actual results on the board
    // additionally, this mechanism allows us to display line clearing.
    spec
      .zip(boardResult)
      .map(tup => greenifyResult(Some(tup._1._2.label), isGreen = true, tup._2))
      .mkString(" -")
  }

}

case class OptimizationFactor(
  enabled:     Boolean,
  key:         String,
  minimize:    Boolean,
  weight:      Double,
  minVal:      Int,
  maxVal:      Int,
  label:       String,
  explanation: String
)

object Specification {

  // one of the optimizations is to ensure that the maximum number of
  // maximum pieces can fit on a board from all the boards simulated in the permutation of a set of pieces
  // apparently it's important that this be declared after Game.CYAN is declared above :)
  // this is not private because we show the maximizer piece at game start
  val maximizer3x3: Piece = GamePieces.bigBox
  val maximizer5x1: Piece = GamePieces.h5Line
  val maximizer1x5: Piece = GamePieces.v5Line

  val maximizerArray: Array[Array[Piece]] = Array(
    maximizer3x3,
    maximizer3x3,
    maximizer3x3,
    maximizer5x1,
    maximizer5x1,
    maximizer5x1,
    maximizer1x5,
    maximizer1x5,
    maximizer1x5

  ).combinations(3).toArray

  val avoidMiddleArray: Array[Array[Int]] = {
    // createes an array of board size
    // where int values in the array as you get closer to the middle
    // get much larger
    // then we add up all board positions where a value is on

    val size = Board.BOARD_SIZE
    val smallestValue = 3
    Array.tabulate(size, size) { (_, _) => 0 }
    Array.tabulate(size, size) { (row, col) =>
      val pos: Int = {
        val rowVal = math.pow(smallestValue, if (row < size / 2) row + 1 else size - row).toInt
        val colVal = math.pow(smallestValue, if (col < size / 2) col + 1 else size - col).toInt
        rowVal.min(colVal)
      }
      pos
    }
  }

  private val avoidMiddleArraySum: Int = avoidMiddleArray.map(row => row.sum).sum

  // println(avoidMiddleArraySum)

  // for readability
  private val minimize = true
  private val maximize = false

  val allMaximizersKey = "allMaximizersKey"
  val avoidMiddleKey = "avoidMiddleKey"
  val lineContiguousUnoccupiedKey = "lineContiguousUnoccupiedKey"
  val maxContiguousKey = "maxContiguousKey"
  val maximizerKey = "maximizerKey"
  val neighborsFourKey = "neighborsFourKey"
  val neighborsThreeKey = "neighborsThreeKey"
  val occupiedKey = "occupiedKey"
  val openLinesKey = "openLinesKey"
  val neighborsTwoKey = "neighborsTwoKey"

  private val totalPositions = math.pow(Board.BOARD_SIZE, 2).toInt

  // todo - OptimizationFactor of all combinations of 3x3 1x5 and 5x1

  // the following code can be paste in by running weightGenerator command line option
  // make sure that if you change any of the key names above, that you change them here as well
/*  private val weightMap = Map(

    "maximizerKey" -> 0.5611371262121603,
    "avoidMiddleKey" -> 0.14843110504774898,
    "occupiedKey" -> 0.07852955274510527,
    "openLinesKey" -> 0.05588289517348181,
    "maxContiguousKey" -> 0.051686884701891526,
    "lineContiguousUnoccupiedKey" -> 0.04175362265403193,
    "neighborsThreeKey" -> 0.03181298624681981,
    "neighborsFourKey" -> 0.020589211312267246,
    "neighborsTwoKey" -> 0.0101766159064931

  )*/

  // from a 390 run
  private val weightMap = Map(

    "maximizerKey" -> 0.5272891017383388,
    "avoidMiddleKey" -> 0.199174613082093,
    "occupiedKey" -> 0.06484840894775809,
    "openLinesKey" -> 0.06024112321467259,
    "maxContiguousKey" -> 0.051175395306053494,
    "lineContiguousUnoccupiedKey" -> 0.041165707917707094,
    "neighborsThreeKey" -> 0.02362527320075932,
    "neighborsFourKey" -> 0.02297247114126007,
    "neighborsTwoKey" -> 0.0095079054513576

  )

  private val allOptimizationFactors = ListMap(

    // you'll need to update class Specification named optFactors above, plus the calls from BoardScore.scores if you change this

    // this one needs to run a subsequent simulation - not yet easy to do
    //allMaximizersCountName -> OptimizationFactor(enabled = true, allMaximizersKey, maximize, weightMap(allMaximizersKey), 0.0, maximizerArray.length, "all maximizers", "count of boards that can fit all combinations (with repeats) of 3x3, 5x1 and 1x5 pieces  - if each piece was placed on the board"),
    avoidMiddleKey -> OptimizationFactor(enabled = true, avoidMiddleKey, minimize, weightMap(avoidMiddleKey), 0, avoidMiddleArraySum, "avoid middle", "unoccupied positions in the middle are bad so score them with a high score"),
    lineContiguousUnoccupiedKey -> OptimizationFactor(enabled = true, lineContiguousUnoccupiedKey, minimize, weightMap(lineContiguousUnoccupiedKey), 20, totalPositions / 2, "spaces on a line", "number of separate spaces on a given line - indicator of how many pieces needed to clear"),
    maxContiguousKey -> OptimizationFactor(enabled = true, maxContiguousKey, maximize, weightMap(maxContiguousKey), 0, Board.BOARD_SIZE, "connected open", "number of lines (either horizontal or vertical) that are open and contiguous"),
    maximizerKey -> OptimizationFactor(enabled = true, maximizerKey, maximize, weightMap(maximizerKey), 0, math.pow(Board.BOARD_SIZE - maximizer3x3.cols + 1, 2).toInt, "maximizer", "positions in which a 3x3 piece can fit"),
    neighborsFourKey -> OptimizationFactor(enabled = true, neighborsFourKey, minimize, weightMap(neighborsFourKey), 0, totalPositions / 2, "4 neighbors", "number of positions surrounded on all 4 sides"),
    neighborsThreeKey -> OptimizationFactor(enabled = true, neighborsThreeKey, minimize, weightMap(neighborsThreeKey), 0, totalPositions / 2, "3 neighbors", "number of positions surrounded on 3 of 4 sides"),
    // i'm really not sure that 60 is the maximum number of two neighbors that can be created on a board
    // but i couldn't find another solution that was better
    neighborsTwoKey -> OptimizationFactor(enabled = true, neighborsTwoKey, minimize, weightMap(neighborsTwoKey), 0, (totalPositions * .6).toInt, "2 neighbors", "number of positions surrounded on 2 of 4 sides"),
    occupiedKey -> OptimizationFactor(enabled = true, occupiedKey, minimize, weightMap(occupiedKey), 0, totalPositions, "occupied", "occupied positions"),
    openLinesKey -> OptimizationFactor(enabled = true, openLinesKey, maximize, weightMap(openLinesKey), 0, Board.BOARD_SIZE + Board.BOARD_SIZE, "open rows + cols", "count of open rows plus open columns")

  )

  private def normalizeOptimizationFactorWeights(optFactors: ListMap[String, OptimizationFactor]): ListMap[String, OptimizationFactor] = {

    // divide each current weight into the previous weight
    // initially current weights were set to 100 (to divide by 100) to mimic the comparison mechanism
    // originally used in this program
    // first pass
    // val weights = Array.tabulate(length)(n => 1 / (math.pow(10, n) * math.pow(10, n)))
    // now using weights from initialWeightFactor
    // val weights = optFactors.values.map(each => each.initialWeightFactor).scanLeft(1.0)((a, b) => a / b).tail
    val weights = optFactors.values.map(each => each.weight)

    // the idea to normalize weights this way is that when you multiply weights times the
    // the normalized values derived from creating the scores, we will get a distribution from 0 to 1
    // Brendan's idea and I like it.  Makes it easy to grok the scores
    // however, just calculating it by mapping each weight over the sum caused a bug
    // when the board clears, the scores are perfect - all normalize to 1.0
    // but when multiplying by normalized weights, we ended up with this number:
    // 1.0000000000000002
    // which is larger than one - and unnacceptable :)
    // so the answer is to take the first n normalized weights and to calculate the last one by
    // subtracting the sum of the first n from 1.  And here it is:
    val sumOfWeights = weights.sum
    val initialNormalized = weights.init.map(_ / sumOfWeights)
    val last: Double = 1.0 - initialNormalized.sum
    val normalizedWeights = initialNormalized ++ Iterable(last)

    val weighted = optFactors.zip(normalizedWeights)
      .map {
        case (specEntry, weight) =>
          specEntry match {
            case (key, opt) => (key, OptimizationFactor(opt.enabled, opt.key, opt.minimize, weight, opt.minVal, opt.maxVal, opt.label, opt.explanation))
          }
      }

    val sortedWeights = ListMap(weighted.toSeq.sortBy(-_._2.weight): _*)
    sortedWeights
  }

  def apply(filtered: Boolean, optFactors: ListMap[String, OptimizationFactor]): Specification = {
    // create a specification with only the filtered parameters
    val weightedFactors = if (filtered)
      normalizeOptimizationFactorWeights(optFactors.filter(opt => opt._2.enabled))
    else
      normalizeOptimizationFactorWeights(optFactors)

    val filteredWeightedSpec: Specification = Specification(weightedFactors)

    filteredWeightedSpec
  }

  def apply(filtered: Boolean): Specification = {
    apply(filtered, allOptimizationFactors)
  }

  // by default return the filtered and weighted specification
  def apply(): Specification = {
    apply(filtered = true)
  }

  def apply(optFactor: OptimizationFactor): Specification = {
    // get a specification just for this optimization factor
    val optFactors = ListMap(optFactor.key -> optFactor)
    apply(filtered = false, optFactors)
  }

  // following are used to construct results
  val maxOptFactorLabelLength: Int = allOptimizationFactors.values.map(_.label.length).max
  val maxOptFactorKeyLength: Int = allOptimizationFactors.values.map(_.key.length).max

  private val weightBufferLength = 13 - StringFormats.weightFormatLength
  private val weightBuffer = " ".repeat(weightBufferLength + 3)

  private val prefixWeightFormatLength = StringFormats.weightFormatLength + 3

  private val piecePrefixLength = maxOptFactorLabelLength + prefixWeightFormatLength + 4

  private val columnPadding = GamePieces.numPiecesInRound * 2

  private val spaceAndVertical = " " + StringFormats.VERTICAL_LINE
  private val permutationColumnWidth = ((GamePieces.widestPiece * 2 - 1) * 3) + 7
  private val scoreColumnString = "score".rightAlignedPadded(columnPadding + 1)
  private val normalizedColumnString = "normalized".rightAlignedPadded(columnPadding + 6)
  private val weightedColumnString = "weighted".rightAlignedPadded(permutationColumnWidth - (scoreColumnString.length + normalizedColumnString.length))
  private val columnHeader = scoreColumnString + normalizedColumnString + weightedColumnString + spaceAndVertical

  /**
   * used for testing purposes (for now) and eventually for an exhaustive run
   * through of all possible permutations and combinations of specifications
   * @return
   */
  def getAllSpecifications: Array[Array[Array[OptimizationFactor]]] = {

    val r = (1 to allOptimizationFactors.size /*length*/ ).toArray

    val combinations = for { i <- r } yield allOptimizationFactors.values.toArray.combinations(i).toArray

    val result = for {
      comboArray <- combinations
      combo <- comboArray
    } yield combo.permutations.toArray

    result

  }

}