import core.lars.Util._
import engine.StreamEntry
import evaluation._
import evaluation.bit.BitProgram

import scala.util.Random

/**
  * Created by FM on 21.08.16.
  */
object BitEvaluation extends BitProgram {

  val all_001 = (0 to maxLevel) map (i => ((xatom(f"signal($i)").atom), 0.01)) toMap
  val all_01 = (0 to maxLevel) map (i => ((xatom(f"signal($i)").atom), 0.1)) toMap


  def main(args: Array[String]): Unit = {
    timings(args)
    //failures(args)
  }

  def timings(args: Seq[String]): Unit = {

    val timePoints = 500

    val evaluateFast = evaluate(_.streamAsFastAsPossible()) _

    // evaluate everything one time as pre-pre-warm up
    evaluateFast(Seq("tms", "learn"),timePoints)

    val dump = DumpData("Configuration", "Programs")
    val dumpToCsv = dump.printResults("bit-output.csv") _

    if (args.length == 0) {
      val callSetups = Seq(
        Seq("tms", "greedy"),
        //        Seq("tms", "doyle"),
        Seq("tms", "learn")
        //        Seq("clingo", "push")
      )

      val allResults = callSetups map (evaluateFast(_,timePoints))
      dump plot allResults
      dumpToCsv(allResults)

    } else {
      val results = evaluateFast(args,timePoints)
      dump plot Seq(results)
      dumpToCsv(Seq(results))
    }
  }

  type Evaluation[C <: ConfigurationResult] = (Evaluator => (Seq[StreamEntry] => C))
  type EvaluationParameters = (Seq[String],Long) //args x timePoints

  def evaluate[C <: ConfigurationResult](evaluation: Evaluation[C])(params:EvaluationParameters) = {

    val (args,timePoints) = params

    val random = new Random(1)

    val namedSignalProbabilities = Seq(
      ("0.001", all_001),
      ("0.01", all_01)
    )

    val program = groundLarsProgram() //TODO optionally load from file

    val preparedSignals = namedSignalProbabilities map { case (instance, prob) =>
      val signals = PrepareEvaluator.generateSignals(prob, random, 0, timePoints)
      (instance, signals)
    }

    val option = args.mkString(" ")

    Console.out.println("Algorithm: " + option)

    val results = preparedSignals map { //TODO time points with no atoms! - call with empty at the moment
      case (instance, signals) => {
        val engine = PrepareEvaluator.fromArguments(args, instance, program)
        evaluation(engine)(signals)
      }
    }

    AlgorithmResult(option, results toList)
  }

  def failures(args: Seq[String]): Unit = {

    val dump = DumpData("Configuration", "Instances")
    val dumpToCsv = dump.printSuccessResults("p18-failure-output.csv") _

    val timePoints = 1000
    val evaluateFailures = evaluate(_.successfulModelComputations) _

    if (args.length == 0) {
      val callSetups = Seq(
        Seq("tms", "greedy"),
        //        Seq("tms", "doyle"),
        Seq("tms", "learn")
        //        Seq("clingo", "push")
      )

      val allResults = callSetups map (evaluateFailures(_,timePoints))
      dump plotFailures allResults
      dumpToCsv(allResults)

    } else {
      val results = evaluateFailures(args,timePoints)
      dump plotFailures Seq(results)
      dumpToCsv(Seq(results))
    }
  }

}


