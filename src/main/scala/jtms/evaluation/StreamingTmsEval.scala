package jtms.evaluation

import java.io.{File, PrintWriter}
import java.util.concurrent.TimeUnit

import common.Util.stopTime
import core.Atom
import core.asp._
import jtms._
import jtms.algorithms.{JtmsDoyle, JtmsGreedy, JtmsLearn}
import jtms.evaluation.instances.{CacheHopsEvalInst, CacheHopsStandardEvalInst, MMediaDeterministicEvalInst, MMediaNonDeterministicEvalInst}
import jtms.networks.{OptimizedNetwork, SimpleNetwork}
import runner.Load

import scala.io.Source


/**
  * Created by hb on 4/4/17.
  */
object StreamingTmsEval {

  //known instances:
  val MMEDIA_DET = "mmediaDet"
  val MMEDIA_NONDET = "mmediaNonDet"
  val CACHE_HOPS = "cacheHops"

  val loader = Load(TimeUnit.SECONDS)

  def main(args: Array[String]): Unit = {
    evaluate(args)
  }

  //implementations:
  val DOYLE_SIMPLE = "DoyleSimple"
  val DOYLE = "Doyle"
  val GREEDY = "Greedy"
  val LEARN = "Learn"

  var INSTANCE_NAME = "inst"
  var TMS = "tms"
  var PRE_RUNS = "pre"
  var RUNS = "runs"
  var TIMEPOINTS = "tp"
  var MODEL_RATIO = "ratio"
  var WINDOW_SIZE = "winsize"
  //
  var ITEMS = "items"
  var EDGE_DIR = "edgedir"
  var EDGE_FILE = "edgeset"
  //
  var POST_PROCESS_GROUNDING = "postProcess"
  var PRINT_RULES = "printRules"

  def evaluate(args: Array[String]): Unit = {

    var argMap = buildArgMap(args)

    def defaultArg(key: String, value: String) = {
      if (!argMap.contains(key)) {
        argMap = argMap + (key -> value)
      }
    }

    defaultArg(INSTANCE_NAME,CACHE_HOPS)
    defaultArg(TMS,DOYLE)
    defaultArg(PRE_RUNS,"2")
    defaultArg(RUNS,"5")
    defaultArg(TIMEPOINTS,"10")
    defaultArg(MODEL_RATIO,"false")
    defaultArg(WINDOW_SIZE,"10")
    //
    defaultArg(ITEMS,"10")
    defaultArg(EDGE_DIR,"src/test/resources/edge-sets/")
    defaultArg(EDGE_FILE,"edges1.txt")
    //    
    defaultArg(POST_PROCESS_GROUNDING,"true")
    defaultArg(PRINT_RULES,"false")

    run(argMap)

  }

  def buildArgMap(args: Array[String]): Map[String,String] = {

    if (args.length % 2 == 1) {
      println("need even number of args. given: "+args)
      System.exit(1)
    }
    if (args.length == 0) {
      return Map()
    }

    var argMap = Map[String,String]()
    for (i <- 0 to args.length/2-1) {
      argMap = argMap + (args(2*i) -> args(2*i+1))
    }
    argMap

  }

  def run(argMap: Map[String,String]) {
    //impl: String, warmUps: Int, iterations: Int, windowSize: Int, timePoints: Int, countModels: Boolean, instanceNames: Seq[String]
    val inst = makeInstance(argMap)
    val tms = argMap(TMS) match {
      case DOYLE_SIMPLE => new JtmsDoyle(new SimpleNetwork(), inst.random)
      case DOYLE => new JtmsDoyle(new OptimizedNetwork(), inst.random)
      case GREEDY => new JtmsGreedy(new OptimizedNetwork(), inst.random)
      case LEARN => new JtmsLearn()
    }
    val preRuns = Integer.parseInt(argMap(PRE_RUNS))
    val runs = Integer.parseInt(argMap(RUNS))
    val modelRatio:Boolean = (argMap(MODEL_RATIO) == "true")

    runImplementation(inst, tms, preRuns, runs, modelRatio)
  }

  def makeInstance(argMap: Map[String,String]): StreamingTmsEvalInstance = {

    val timePoints = Integer.parseInt(argMap(TIMEPOINTS))
    val windowSize = Integer.parseInt(argMap(WINDOW_SIZE))
    val nrOfItems = Integer.parseInt(argMap(ITEMS))
    val postProcessGrounding = (argMap(POST_PROCESS_GROUNDING) == "true")

    argMap(INSTANCE_NAME) match {
      case CACHE_HOPS => {
        val edges: Set[Atom] = CacheHopsEvalInst.loadEdges(argMap(EDGE_DIR),argMap(EDGE_FILE))
        val printRules: Boolean = (argMap(PRINT_RULES) == "true")
        CacheHopsStandardEvalInst(windowSize,timePoints,nrOfItems,edges,postProcessGrounding,printRules)
      }
      case MMEDIA_DET => MMediaDeterministicEvalInst(windowSize, timePoints)
      case MMEDIA_NONDET => MMediaNonDeterministicEvalInst(windowSize, timePoints)
      case s => println(f"unknown instance name $s"); throw new RuntimeException
    }
  }

  def runImplementation(instance: StreamingTmsEvalInstance, tms: JtmsUpdateAlgorithm, preRuns: Int, runs: Int, modelRatio: Boolean): Unit = {

    var totalTime = 0L
    var totalRetractions = 0L
    var totalModels = 0L
    var totalFailures = 0L
    var totalTimeRuleGen = 0L

    var totalTimeStaticRules = 0L
    var totalTimeAllTimePoints = 0L
    var totalTimeAddFact = 0L
    var totalTimeAddRule = 0L
    var totalTimeRemoveRule = 0L
    var totalTimeRemoveFact = 0L
    var totalTimeGetModel = 0L
    var totalNrStaticRules = 0L
    var totalNrAddFact = 0L
    var totalNrAddRule = 0L
    var totalNrRemoveRule = 0L
    var totalNrRemoveFact = 0L

    for (i <- (1 + (preRuns * -1)) to runs) {

      print(" " + i)

      val result: Map[String, Long] = runIteration(instance, tms, modelRatio)

      if (i >= 1) {
        totalTime += result(_evaluationIterationTime)
        totalModels += result(_models)
        totalFailures += result(_failures)
        totalTimeRuleGen += result(_timeRuleGen)
        totalTimeAllTimePoints += result(_timeAllTimePoints)
        totalTimeStaticRules += result(_timeStaticRules)
        totalTimeAddFact += result(_timeAddFacts)
        totalTimeAddRule += result(_timeAddRules)
        totalTimeRemoveRule += result(_timeRemoveRules)
        totalTimeRemoveFact += result(_timeRemoveFacts)
        totalTimeGetModel += result(_timeGetModel)
        totalNrStaticRules += result(_nrOfStaticRules)
        totalNrAddFact += result(_nrOfAddedFacts)
        totalNrAddRule += result(_nrOfAddedRules)
        totalNrRemoveRule += result(_nrOfRemovedRules)
        totalNrRemoveFact += result(_nrOfRemovedFacts)
      }

      if (instance.isInstanceOf[JtmsDoyle]) { //TODO revisit later when optimized version is there
        totalRetractions = totalRetractions + tms.asInstanceOf[JtmsDoyle].retractionsAffected
      }

    }

    case class LongDiv(l: Long) {
      def %% (other: Long): Double = (1.0*l) / (1.0*other)
    }

    case class DoubleSec(d: Double) {
      def sec(): Double = d / 1000.0
    }

    implicit def long2div(l: Long) = LongDiv(l)
    implicit def double2sec(d: Double) = DoubleSec(d)

    val avgTimeIteration = totalTime %% runs sec
    val avgTimeRuleGen = totalTimeRuleGen %% runs sec
    val avgTimeStaticRules = totalTimeStaticRules %% runs sec
    val avgTimeAllTimePoints = totalTimeAllTimePoints %% (runs * instance.timePoints) sec
    val avgTimeAddFact = totalTimeAddFact %% totalNrAddFact sec
    val avgTimeAddRule = totalTimeAddRule %% totalNrAddRule sec
    val avgTimeRemoveRule = totalTimeRemoveRule %% totalNrRemoveRule sec
    val avgTimeRemoveFact = totalTimeRemoveFact %% totalNrRemoveFact sec
    val avgTimeGetModel = totalTimeGetModel %% (runs * instance.timePoints) sec
    val totalUpdates = totalModels + totalFailures
    val ratioModels = totalModels %% totalUpdates
    val ratioFailures = totalFailures %% totalUpdates

    println(f"\navg time per iteration: $avgTimeIteration sec")
    println(f"avg time rule gen (not included): $avgTimeRuleGen sec")
    println(f"avg time add static rules: $avgTimeStaticRules sec")
    println(f"avg time per time point: $avgTimeAllTimePoints sec")
    println(f"avg time add fact: $avgTimeAddFact sec")
    println(f"avg time add rule: $avgTimeAddRule sec")
    println(f"avg time remove rule: $avgTimeRemoveRule sec")
    println(f"avg time remove fact: $avgTimeRemoveFact sec")
    println(f"avg time get model: $avgTimeGetModel sec")
    println(f"ratio models: $ratioModels")
    println(f"ratio failures: $ratioFailures")

    if (instance.isInstanceOf[JtmsDoyle]) {
      val avgRetractions = (1.0 * totalRetractions) / (1.0 * runs)
      println(f"avg retractions: $avgRetractions")
    }

  }

  val _evaluationIterationTime = "evaluationIterationTime"
  val _models = "models"
  val _failures = "failures"
  val _timeRuleGen = "timeRuleGen" //only internal info
  val _timeStaticRules = "timeStaticRules"
  val _timeAllTimePoints = "timeAllTimePoints"
  val _timeAddFacts = "timeAddFact"
  val _timeAddRules = "timeAddRule"
  val _timeRemoveRules = "timeRemoveRule"
  val _timeRemoveFacts = "timeRemoveFact"
  val _timeGetModel = "timeGetModel"
  val _nrOfStaticRules = "nrOfInitRules"
  val _nrOfAddedFacts = "nrAddFact"
  val _nrOfAddedRules = "nrAddRule"
  val _nrOfRemovedRules = "nrRemoveRule"
  val _nrOfRemovedFacts = "nrRemoveFact"

  def runIteration(inst: StreamingTmsEvalInstance, tms: JtmsUpdateAlgorithm, countModels: Boolean): Map[String, Long] = {

    var models = 0L
    var failures = 0L

    val timeStaticRules: Long = stopTime {
      inst.staticRules foreach tms.add
    }
    val nrOfStaticRules: Long = inst.staticRules.size

    var timeAllTimePoints = 0L
    var timeRuleGen = 0L
    var timeAddFacts = 0L
    var timeAddRules = 0L
    var timeRemoveRules = 0L
    var timeRemoveFacts = 0L
    var timeGetModel = 0L
    var nrOfAddedFacts = 0L
    var nrOfAddedRules = 0L
    var nrOfRemovedRules = 0L
    var nrOfRemovedFacts = 0L

    for (t <- 0 to inst.timePoints) {

      var factsToAdd = Seq[NormalRule]()
      var rulesToAdd = Seq[NormalRule]()
      var rulesToRemove = Seq[NormalRule]()
      var factsToRemove = Seq[NormalRule]()

      timeRuleGen = timeRuleGen + stopTime {
        factsToAdd = inst.factsToAddAt(t)
        rulesToAdd = inst.rulesToAddAt(t)
        rulesToRemove = inst.rulesToRemoveAt(t)
        factsToRemove = inst.factsToRemoveAt(t)
        nrOfAddedFacts += factsToAdd.size
        nrOfAddedRules += rulesToAdd.size
        nrOfRemovedRules += rulesToRemove.size
        nrOfRemovedFacts += factsToRemove.size
      }

      var loopTimeAddFacts = 0L
      var loopTimeAddRules = 0L
      var loopTimeRemoveRules = 0L
      var loopTimeRemoveFacts = 0L
      var loopTimeGetModel = 0L

      factsToAdd foreach { r =>
        //println("add "+r)
        loopTimeAddFacts += stopTime { tms.add(r) }
        if (countModels) {
          if (tms.getModel.isDefined) models += 1
          else failures += 1
        }
      }

      rulesToAdd foreach { r =>
        //println("add "+r)
        loopTimeAddRules += stopTime { tms.add(r) }
        if (countModels) {
          if (tms.getModel.isDefined) models += 1
          else failures += 1
        }
      }

      rulesToRemove foreach { r =>
        //println("remove "+r)
        loopTimeRemoveRules += stopTime { tms.remove(r) }
        if (countModels) {
          if (tms.getModel.isDefined) models += 1
          else failures += 1
        }
      }

      factsToRemove foreach { r =>
        //println("remove "+r)
        loopTimeRemoveFacts += stopTime { tms.remove(r) }
        if (countModels) {
          if (tms.getModel.isDefined) models += 1
          else failures += 1
        }
      }

      loopTimeGetModel += stopTime { tms.getModel }

      val loopTime = loopTimeAddFacts + loopTimeAddRules + loopTimeRemoveRules + loopTimeRemoveFacts + loopTimeGetModel

      timeAllTimePoints += loopTime
      timeAddFacts += loopTimeAddFacts
      timeAddRules += loopTimeAddRules
      timeRemoveRules += loopTimeRemoveRules
      timeRemoveFacts += loopTimeRemoveFacts
      timeGetModel += loopTimeGetModel

      inst.verifyModel(tms,t)

    }

    val evaluationIterationTime = timeStaticRules + timeAllTimePoints

    /*
    if (tms.isInstanceOf[JtmsDoyle]) {
      val jtms = tms.asInstanceOf[JtmsDoyle]
      jtms.doConsistencyCheck = true
      jtms.doJtmsSemanticsCheck = true
      jtms.doSelfSupportCheck = true
      jtms.checkConsistency()
      jtms.checkJtmsSemantics()
      jtms.checkSelfSupport()
    }
    */

    Map() + (_evaluationIterationTime -> evaluationIterationTime) + (_models -> models) + (_failures -> failures) + (_timeRuleGen -> timeRuleGen) +
        (_timeStaticRules -> timeStaticRules) + (_timeAllTimePoints -> timeAllTimePoints) +
        (_timeAddFacts -> timeAddFacts) + (_timeAddRules -> timeAddRules) +
        (_timeRemoveRules -> timeRemoveRules) + (_timeRemoveFacts -> timeRemoveFacts) + (_timeGetModel -> timeGetModel) +
        (_nrOfStaticRules -> nrOfStaticRules) + (_nrOfAddedFacts -> nrOfAddedFacts) + (_nrOfAddedRules -> nrOfAddedRules) +
        (_nrOfRemovedRules -> nrOfRemovedRules) + (_nrOfRemovedFacts -> nrOfRemovedFacts)

  }

  def readProgramFromFile(filename: String): NormalProgram = {
    //val source = Source.fromURL(getClass.getResource(filename))
    val source = Source.fromFile(new File(filename))
    val rules = source.getLines().toSeq map (l => Util.asAspRule(loader.rule(l)))
    AspProgram(rules.toList)
  }

  def writeProgramToFile(program: NormalProgram, filename: String) = {
    val pw = new PrintWriter(new File(filename))
    program.rules foreach (r => pw.write(r.toString + "\n"))
    pw.close
  }

  def printModel(tms: JtmsUpdateAlgorithm): Unit = {
    println("model for " + tms.getClass.getSimpleName)
    tms.getModel match {
      case Some(m) => println(m); println("#atoms: " + m.size)
      case None => println("none")
    }
  }

  /*
  test("infinite odd loop doyle") {

    val tms = new JtmsDoyle()
    val r1 = asAspRule(rule("a :- not b"))
    val r2 = asAspRule(rule("b :- a"))
    tms add r1
    tms add r2

    println(tms.getModel)
  }
  */

}