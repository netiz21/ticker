package jtms

import core._

import scala.annotation.tailrec
import scala.collection.mutable.{HashMap, Map, Set}

object TMN {

  def apply(P: Program) = {
    val tmn = new TMN()
    P.rules foreach tmn.add
    tmn
  }

}

/**
  * truth maintenance network
  * Created by hb on 12/22/15.
  */
case class TMN() {

  var rules: List[Rule] = List()

  val Cons: Map[Atom, Set[Atom]] = new HashMap[Atom, Set[Atom]]
  val Supp: Map[Atom, Set[Atom]] = new HashMap[Atom, Set[Atom]] //TODO pos vs neg
  val SuppRule: Map[Atom, Option[Rule]] = new HashMap[Atom, Option[Rule]]
  val status: Map[Atom, Status] = new HashMap[Atom, Status] //at least 'in' consequence of SuppRule

  def registerAtom(a: Atom): Unit = {
    if (!status.isDefinedAt(a)) status(a) = out
    if (!Cons.isDefinedAt(a)) Cons(a) = Set[Atom]()
    if (!Supp.isDefinedAt(a)) Supp(a) = Set[Atom]()
    if (!SuppRule.isDefinedAt(a)) SuppRule(a) = None
  }

  def atoms() = Cons.keySet

  def getModel(): Option[scala.collection.immutable.Set[Atom]] = {
    val atoms = inAtoms()
    if (atoms exists contradictionAtom) {
      return None
    }
    Some(atoms.toSet)
  }

  def inAtoms() = status.keys filter (status(_) == in)

  //TMS update algorithm
  def add(rule: Rule): Unit = {
    register(rule)
    if (status(rule.head) == in || invalid(rule)) return
    updateBeliefs(repercussions(rule.head) + rule.head)
  }

  def register(rule: Rule): Unit = {
    if (rules contains rule) return //list representation!
    rules = rules :+ rule
    rule.atoms foreach registerAtom
    rule.body foreach (Cons(_) += rule.head)
  }

  def invalid(rule: Rule): Boolean = {
    findSpoiler(rule) match {
      case Some(spoiler) => { Supp(rule.head) += spoiler; true }
      case None => false
    }
  }

  def updateBeliefs(atoms: Set[Atom]): collection.immutable.Set[Atom] = stateDiff {
    atoms foreach setUnknown //Marking the nodes
    atoms foreach determineAndPropagateStatus // Evaluating the nodes' justifications
    atoms foreach assumeAndPropagateStatus // Relaxing circularities (might lead to contradictions)
    tryEnsureConsistency
  }

  def stateDiff(expression: => () => Unit): collection.immutable.Set[Atom] = {
    val oldState = stateOfAtoms
    expression()
    val newState = stateOfAtoms
    (oldState diff newState) map (_._1) toSet
  }

  def stateOfAtoms() = atoms map (a => (a, status(a))) toList

  def isInvalid(rule: Rule): Boolean = { //TODO
    findSpoiler(rule) match {
      case Some(spoiler) => { Supp(rule.head) += spoiler; true }
      case None => false
    }
  }

  def rulesWithHead(h: Atom) = rules filter (_.head == h)

  //ACons(a) = {x ∈ Cons(a) | a ∈ Supp(x)}
  def ACons(a: Atom): Set[Atom] = Cons(a) filter (Supp(_).contains(a))

  def repercussions(n: Atom) = trans(ACons, n)

  def setIn(rule: Rule) = {
    status(rule.head) = in
    Supp(rule.head) = Set() ++ rule.body
    SuppRule(rule.head) = Some(rule)
  }

  def setOut(a: Atom) = {
    status(a) = out
    Supp(a) = Set() ++ (rulesWithHead(a) map (findSpoiler(_).get))
    SuppRule(a) = None
  }

  def setUnknown(atom: Atom): Unit = {
    status(atom) = unknown
    Supp(atom) = Set()
    SuppRule(atom) = None
  }

  def findSpoiler(rule: Rule): Option[Atom] = {
    if (math.random < 0.5) {
      rule.pos find (status(_) == out) match {
        case None => rule.neg find (status(_) == in)
        case opt => opt
      }
    } else {
      rule.neg find (status(_) == in) match {
        case None => rule.pos find (status(_) == out)
        case opt => opt
      }
    }
  }

  def unknownCons(a: Atom) = Cons(a) filter (status(_) == unknown)

  def determineAndPropagateStatus(a: Atom): Unit = {
    if (status(a) != unknown)
      return

    if (validation(a) || invalidation(a)) {
        unknownCons(a) foreach determineAndPropagateStatus
    }
  }

  def validation(a: Atom): Boolean = {
    rulesWithHead(a) find foundedValid match {
      case Some(rule) => { setIn(rule); true }
      case None => false
    }
  }

  def invalidation(a: Atom): Boolean = {
    if (rulesWithHead(a) forall foundedInvalid) {
      setOut(a)
      return true
    }
    false
  }

  def assumeAndPropagateStatus(a: Atom): Unit = {
    if (status(a) != unknown)
      return

    if (assume(a)) {
      unknownCons(a) foreach assumeAndPropagateStatus
    } else {
      for (c <- (ACons(a) + a)) {
        setUnknown(c)
        assumeAndPropagateStatus(c)
      }
      //TODO (HB) shouldn't it be setUnknown for all, then fix.. for all?
    }
  }

  def assume(a: Atom): Boolean = {
    rulesWithHead(a) find unfoundedValid match {
      case Some(rule) => {
        if (ACons(a).isEmpty) assumeIn(rule)
        else return false
      }
      case None => assumeOut(a)
    }
    true
  }

  def assumeIn(unfoundedValidRule: Rule) = {
    unfoundedValidRule.neg filter (status(_) == unknown) foreach setOut //create foundation
    setIn(unfoundedValidRule)
  }

  def assumeOut(a: Atom) = {
    val unknownPosAtoms = rulesWithHead(a) map { r => (r.pos find (status(_)==unknown)).get }
    unknownPosAtoms foreach setOut
    setOut(a)
  }

  def foundedValid(rule: Rule): Boolean = {
    (rule.pos forall (status(_) == in)) && (rule.neg forall (status(_) == out))
  }

  def foundedInvalid(rule: Rule): Boolean = {
    (rule.pos exists (status(_) == out)) || (rule.neg exists (status(_) == in))
  }

  def unfoundedValid(rule: Rule): Boolean = {
    (rule.pos forall (status(_) == in)) && (!(rule.neg exists (status(_) == in))) && (rule.neg exists (status(_) == unknown))
  }

  def trans[T](f: T => Set[T], t: T): Set[T] = {
    trans(f)(f(t))
  }

  @tailrec
  final def trans[T](f: T => Set[T])(s: Set[T]): Set[T] = {
    val next = s.flatMap(f)
    val nextSet = next ++ s
    if (s == nextSet || next.isEmpty) {
      return s
    }
    trans(f)(nextSet)
  }

  //return if methods leaves without contradiction
  def tryEnsureConsistency(): Unit = {
    for (c <- inAtoms() filter contradictionAtom) {
      if (!DDB(c)) return
    }
  }

  def contradictionAtom(a: Atom) = a.isInstanceOf[ContradictionAtom]

  //return true if method leaves with status(c) != in
  def DDB(c: Atom): Boolean = {

    if (status(c) != in) return true

    val asms = foundations(c) filter isAssumption
    val maxAssumptions = asms filter { a =>
      ! ((asms - a) exists (b => foundations(b) contains a))
    }

    if (maxAssumptions.isEmpty)
      return false //contradiction cannot be solved

    findBacktrackingRule2(maxAssumptions) match {
      case Some(rule) => { add(rule); return true }
      case None => return false
    }

  }

  //book chapter
  def findBacktrackingRule(maxAssumptions: Set[Atom]): Option[RuleFromBacktracking] = {
    val n = SuppRule(maxAssumptions.head).get.neg.head

    val suppRules = maxAssumptions map (SuppRule(_).get)
    val pos = suppRules flatMap (_.pos)
    val neg = (suppRules flatMap (_.neg)) - n
    val rule = RuleFromBacktracking(pos,neg,n)

    assert(foundedValid(rule))

    Some(rule)
  }

  def findBacktrackingRule2(maxAssumptions: Set[Atom]): Option[RuleFromBacktracking] = {

    //val suppRules = (maxAssumptions map (SuppRule(_).get)).toSet

    var rule:Option[RuleFromBacktracking] = None
    var assumptionCandidates = List[Atom]() ++ maxAssumptions

    //TODO (hb) refactor
    while (rule == None && !assumptionCandidates.isEmpty) {
      val nA = assumptionCandidates.head
      val supportingRule = SuppRule(nA).get
      var nStarCandidates = Set[Atom]() ++ supportingRule.neg
      val supportingRuleCandidates = (assumptionCandidates map (SuppRule(_).get)).toSet
      while (rule == None && !nStarCandidates.isEmpty) {
        val nStar = nStarCandidates.head
        rule = createValidRuleForBacktracking(supportingRuleCandidates, nStar)
        nStarCandidates = nStarCandidates.tail
      }
      assumptionCandidates = assumptionCandidates.tail
    }

    rule
  }

  def createValidRuleForBacktracking(suppRules: collection.immutable.Set[Rule], nStar: Atom): Option[RuleFromBacktracking] = {

    val pos = suppRules flatMap (_.pos)
    val neg = (suppRules flatMap (_.neg)) - nStar

    //if (pos.isEmpty && neg.isEmpty) return None //TODO (hb) avoid facts ~> JTMS_21
    val rule = RuleFromBacktracking(pos,neg,nStar)

    if (this.rules contains rule) return None //TODO (hb)

    assert(foundedValid(rule))

    Some(rule)
  }

  def antecedents(a: Atom): Set[Atom] = {
    if (status(a) == in) return Supp(a)
    Set()
  }

  def foundations(a: Atom) = trans(antecedents, a)

  def ancestors(n: Atom) = trans(Supp, n)

  def isAssumption(a: Atom) = (status(a) == in) && !SuppRule(a).get.neg.isEmpty


  //TODO (hb) use List instead of Set s.t. consecutive removals amount to undo (same results as before)
  //in particular: Rule: List of pos/neg body atoms
  def remove(rule: Rule) = {

    val head = rule.head

    val rulesFromBacktracking = rules.filter(_.isInstanceOf[RuleFromBacktracking])

    def affectedAtoms(a: Atom) = repercussions(a) + a

    var L = affectedAtoms(head) ++ rulesFromBacktracking.flatMap(x => affectedAtoms(x.head))

    def removeRule(rule: Rule) = {
      for (m <- rule.body) {
        Cons(m) -= rule.head //TODO (HB) not necessarily
      }

      rules = rules filter (_ != rule)
    }

    removeRule(rule)

    rulesFromBacktracking.foreach(removeRule)

    // if no other rule exists containing the atom - remove it completely
    if (!rules.exists(_.atoms.contains(head))) {
      //atoms -= head
      status.remove(head)
      Supp.remove(head)
      SuppRule.remove(head)
      Cons.remove(head)
      L -= head
    }

    this.updateBeliefs(L)
  }

  // ----------------- test stuff or stuff that might not be needed ----------------

  /** @return true if M is admissible **/
  def set(M: collection.immutable.Set[Atom]): Boolean = { //TODO (HB) Set vs List. Always list for order?
  val m = M.toList
    for (i <- 0 to M.size - 1) {
      val rule: Option[Rule] = findSuppRule(m, i)
      if (rule.isEmpty) {
        return false
      }
      setIn(rule.get)
    }
    for (n <- atoms diff M) {
      setOut(n)
    }
    true
  }


  /** takes atoms at list M index idx and tries to find a valid rule
    * that is founded wrt indexes 0..idx-1
    */
  def findSuppRule(M: List[Atom], idx: Int): Option[Rule] = {
    val n = M(idx)
    val MSub = M.take(idx).toSet
    val rules = rulesWithHead(n).filter(rule => rule.pos.subsetOf(MSub) && rule.neg.intersect(M.toSet).isEmpty)
    selectRule(rules)
  }

  def selectRule(rules: List[Rule]): Option[Rule] = {
    if (rules.isEmpty)
      return None
    Some(rules.head)
  }

}