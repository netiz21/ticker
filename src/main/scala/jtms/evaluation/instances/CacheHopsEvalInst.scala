package jtms.evaluation.instances

import core._
import core.asp.{AspProgram, NormalRule}
import core.grounding.{GrounderInstance, StaticProgramInspection}
import jtms.JtmsUpdateAlgorithm
import jtms.evaluation.StreamingTmsEvalInstance

import scala.util.Random

/**
  * Created by hb on 10.04.17.
  */
abstract class CacheHopsEvalInst(random: Random = new Random()) extends StreamingTmsEvalInstance {

  def windowSize: Int
  def timePoints: Int
  def nrOfItems: Int
  def edges: Set[Atom]
  def postProcessGrounding: Boolean

  final def nodes(): Set[Value] = edges collect { case a:GroundAtomWithArguments => a.arguments } flatten
  final def nodeAtoms(): Set[Atom] = nodes map (node(_))

  val _node = Predicate("node")
  val _sat = Predicate("sat")
  val _item = Predicate("item")
  val _getFrom = Predicate("getFrom")
  val _n_getFrom = Predicate("n_getFrom")
  val _needAt = Predicate("needAt")
  val _conn = Predicate("conn")
  val _edge = Predicate("edge")
  val _minReach = Predicate("minReach")
  val _n_minReach = Predicate("n_minReach")
  val _itemReach = Predicate("itemReach")
  val _reach = Predicate("reach")

  //do not use predicates used in grounding since we do not want to re-ground here
  val _notEq = Predicate("notEq") //corresponds to neq
  val _lowerThan = Predicate("lowerThan") //corresponds to lt
  val _incr = Predicate("incr") //inr(K0,K) corresponds to plus(K0,1,K)
  //windows
  val _w_req = Predicate("w_req")
  val _w_cache = Predicate("w_cache")
  val _w_error = Predicate("w_error")
  //signals
  val _req = Predicate("req")
  val _cache = Predicate("cache")
  val _error = Predicate("error")

  def node(arg1: Argument) = AtomWithArguments(_node,Seq(arg1))
  def sat(arg1: Argument, arg2: Argument) = AtomWithArguments(_sat,Seq(arg1,arg2))
  def getFrom(arg1: Argument, arg2: Argument, arg3: Argument) = AtomWithArguments(_getFrom,Seq(arg1,arg2,arg3))
  def item(arg1: Argument) = AtomWithArguments(_item,Seq(arg1))
  def n_getFrom(arg1: Argument, arg2: Argument, arg3: Argument) = AtomWithArguments(_n_getFrom,Seq(arg1,arg2,arg3))
  def needAt(arg1: Argument, arg2: Argument) = AtomWithArguments(_needAt,Seq(arg1,arg2))
  def conn(arg1: Argument, arg2: Argument) = AtomWithArguments(_conn,Seq(arg1,arg2))
  def edge(arg1: Argument, arg2: Argument) = AtomWithArguments(_edge,Seq(arg1,arg2))
  def minReach(arg1: Argument, arg2: Argument, arg3: Argument) = AtomWithArguments(_minReach,Seq(arg1,arg2,arg3))
  def n_minReach(arg1: Argument, arg2: Argument, arg3: Argument, arg4: Argument) = AtomWithArguments(_n_minReach,Seq(arg1,arg2,arg3,arg4))
  def itemReach(arg1: Argument, arg2: Argument, arg3: Argument, arg4: Argument) = AtomWithArguments(_itemReach,Seq(arg1,arg2,arg3,arg4))
  def reach(arg1: Argument, arg2: Argument, arg3: Argument) = AtomWithArguments(_reach,Seq(arg1,arg2,arg3))
  def notEq(arg1: Argument, arg2: Argument) = AtomWithArguments(_notEq,Seq(arg1,arg2))
  def lowerThan(arg1: Argument, arg2: Argument) = AtomWithArguments(_lowerThan,Seq(arg1,arg2))
  def incr(arg1: Argument, arg2: Argument) = AtomWithArguments(_incr,Seq(arg1,arg2))
  def w_req(arg1: Argument, arg2: Argument) = AtomWithArguments(_w_req,Seq(arg1,arg2))
  def w_cache(arg1: Argument, arg2: Argument) = AtomWithArguments(_w_cache,Seq(arg1,arg2))
  def w_error(arg1: Argument, arg2: Argument) = AtomWithArguments(_w_error,Seq(arg1,arg2))
  def req(arg1: Argument, arg2: Argument, arg3: Argument) = AtomWithArguments(_req,Seq(arg1,arg2,arg3))
  def cache(arg1: Argument, arg2: Argument, arg3: Argument) = AtomWithArguments(_cache,Seq(arg1,arg2,arg3))
  def error(arg1: Argument, arg2: Argument, arg3: Argument) = AtomWithArguments(_error,Seq(arg1,arg2,arg3))

  /*
   % STATIC:
   sat(I,N) :- item(I), node(N), w_req(I,N), w_cache(I,N).
   sat(I,N) :- item(I), node(N), w_req(I,N), getFrom(I,N,M).
   needAt(I,N) :- item(I), node(N), w_req(I,N), not w_cache(I,N).
   conn(N,M) :- edge(N,M), not w_error(N,M).
   getFrom(I,N,M) :- needAt(I,N), minReach(I,N,M), not n_getFrom(I,N,M).
   n_getFrom(I,N,M2) :- getFrom(I,N,M), minReach(I,N,M2), M != M2.
   minReach(I,N,M) :- itemReach(I,N,M,K), not n_minReach(I,N,M,K).
   n_minReach(I,N,M,K) :- itemReach(I,N,M,K), itemReach(I,N,M2,K2), K2 < K.
   itemReach(I,N,M,K) :- needAt(I,N), w_cache(I,M), reach(N,M,K).
   reach(N,M,1) :- conn(N,M).
   reach(N,M,K) :- reach(N,M0,K0), conn(M0,M), N!=M, K=K0+1.

   %DYNAMIC:
   w_req(I,N) :- req(I,N,T)
   w_cache(I,N) :- cache(I,N,T)
   w_error(N,M) :- error(N,M,T)
   */

  var rulesPrinted = true

  override def staticRules(): Seq[NormalRule] = {

    val I:Variable = StringVariable("I")
    val N:Variable = StringVariable("N")
    val M:Variable = StringVariable("M")
    val M0:Variable = StringVariable("M0")
    val M2:Variable = StringVariable("M2")
    val K:Variable = Variable("K")
    val K0:Variable = Variable("K0")
    val K2:Variable = Variable("K2")

    var rules = Seq[NormalRule]()
    def s(ats: Atom*) = ats.toSet[Atom]


    rules = rules :+
    //   sat(I,N) :- item(I), node(N), w_req(I,N), w_cache(I,N).
    rule(sat(I,N), s(item(I), node(N), w_req(I,N), w_cache(I,N)), s()) :+
    //   sat(I,N) :- item(I), node(N), w_req(I,N), getFrom(I,N,M).
    rule(sat(I,N), s(item(I), node(N), w_req(I,N), getFrom(I,N,M)), s()) :+
    //  needAt(I,N) :- item(I), node(N), w_req(I,N), not w_cache(I,N).
    rule(needAt(I,N), s(item(I), node(N), w_req(I,N)),  s(w_cache(I,N)) ) :+
    //   conn(N,M) :- edge(N,M), not w_error(N,M).
    rule(conn(N,M), s(edge(N,M)),  s(w_error(N,M))) :+
    //   getFrom(I,N,M) :- needAt(I,N), minReach(I,N,M), not n_getFrom(I,N,M).
    rule(getFrom(I,N,M), s(needAt(I,N), minReach(I,N,M)),  s(n_getFrom(I,N,M))) :+
    //   n_getFrom(I,N,M2) :- getFrom(I,N,M), minReach(I,N,M2), M != M2.
    rule(n_getFrom(I,N,M2), s(getFrom(I,N,M), minReach(I,N,M2), notEq(M,M2)), s()) :+
    //   minReach(I,N,M) :- itemReach(I,N,M,K), not n_minReach(I,N,M,K).
    rule(minReach(I,N,M), s(itemReach(I,N,M,K)),  s(n_minReach(I,N,M,K))) :+
    //   n_minReach(I,N,M,K) :- itemReach(I,N,M,K), itemReach(I,N,M2,K2), K2 < K.
    rule(n_minReach(I,N,M,K), s(itemReach(I,N,M,K), itemReach(I,N,M2,K2), lowerThan(K2,K)), s()) :+
    //   itemReach(I,N,M,K) :- needAt(I,N), w_cache(I,M), reach(N,M,K).
    rule(itemReach(I,N,M,K), s(needAt(I,N), w_cache(I,M), reach(N,M,K)), s()) :+
    //   reach(N,M,1) :- conn(N,M).
    rule(reach(N,M,IntValue(1)), s(conn(N,M)), s()) :+
    //   reach(N,M,K) :- reach(N,M0,K0), conn(M0,M), N!=M, incr(K0,K).
    rule(reach(N,M,K), s(reach(N,M0,K0), conn(M0,M), notEq(N,M), incr(K0,K)), s()) //do not use 'plus(K0,1,K)' instead of incr. grounder cannot resolve

    /* facts */

    rules = rules ++ (edges map (fact(_)))
    rules = rules ++ (nodeAtoms map (fact(_)))
    for (i <- 1 to nrOfItems) {
      rules = rules :+ fact(item("i"+i))
    }


    /* auxiliary relations lowerThan, incr, notEq */

    for (i <- 0 to (nodes.size - 1)) { //maximum reachability is for number of nodes
      for (j <- (i+1) to (nodes.size)) {
        rules = rules :+ fact(lowerThan(IntValue(i),IntValue(j)))
      }
    }
    for (i <- 0 to nodes.size) {
      rules = rules :+ fact(incr(IntValue(i),IntValue(i+1)))
    }
    for (n <- nodes) {
      for (m <- nodes) {
        if (n != m) {
          rules = rules :+ fact(notEq(n,m)) :+ fact(notEq(m,n))
        }
      }
    }

    //

    val program = AspProgram(rules.toList)
    val inspect = StaticProgramInspection.forAsp(program)
    val grounder = GrounderInstance.oneShotAsp(inspect)

    //TODO curr inspect how reach(N,M,1) rules fails in grounding
    var groundRules = rules flatMap (grounder.ground(_))

    //TODO also remove facts item, nodes and their occurrences in rule bodies
    if (postProcessGrounding) {
      groundRules = postProcess(groundRules)
    }

    if (!rulesPrinted) {
      println()
      groundRules foreach println
      rulesPrinted = true
    }

    println(f"\n${groundRules.size} ground rules")

    groundRules

  }

  //delete aux relation atoms, as facts and from rules where they hold
  //delete rules where they do not hold
  def postProcess(groundRules: Seq[NormalRule]): Seq[NormalRule] = {
    groundRules collect {
      case r if (!isRelation(r.head) && !unsatisfiedRelation(r)) => removeRelations(r)
    }
  }

  def unsatisfiedRelation(r: NormalRule): Boolean = {
    val relations = r.body filter (isRelation(_))
    relations exists (a => !(satisfied(a)))
  }

  def satisfied(relation: Atom): Boolean = {
    val args = relation.arguments()
    relation.predicate match {
      case `_notEq` => args(0) != args(1)
      case `_lowerThan` => Integer.parseInt(""+args(0)) < Integer.parseInt(""+args(1))
      case `_incr` => Integer.parseInt(""+args(0)) + 1 == Integer.parseInt(""+args(1))
    }
  }

  def isRelation(a: Atom): Boolean = {
    val h = a.predicate
    h == _notEq || h == _lowerThan || h == _incr
  }

  def removeRelations(r: NormalRule): NormalRule = {
    val h = r.head
    val p = r.pos filter (a => !(isRelation(a)))
    val n = r.neg
    rule(h,p,n)
  }

}

case class CacheHopsStandardEvalInst(windowSize: Int, timePoints: Int, nrOfItems: Int, edges: Set[Atom], postProcessGrounding: Boolean, printRules: Boolean, random: Random = new Random()) extends CacheHopsEvalInst(random) {

  if (printRules) {
    rulesPrinted = false
  }

  override def factsToAddAt(t: Int): Seq[NormalRule] = Seq()
  override def rulesToAddAt(t: Int): Seq[NormalRule] = Seq()
  override def rulesToRemoveAt(t: Int): Seq[NormalRule] = Seq()
  override def factsToRemoveAt(t: Int): Seq[NormalRule] = Seq()
  override def verifyModel(tms: JtmsUpdateAlgorithm, t: Int) = {}

}

object CacheHopsEvalInst {
  def loadEdges(dir: String, filename: String): Set[Atom] = {
    val _edge = Predicate("edge")
    val source = scala.io.Source.fromFile(dir + filename)
    val atoms = source.getLines map { l =>
      val line = l.trim
      val arr = line.split(" ")
      GroundAtomWithArguments(_edge, Seq(arr(0), arr(1)))
    }
    atoms.toSet
  }
}