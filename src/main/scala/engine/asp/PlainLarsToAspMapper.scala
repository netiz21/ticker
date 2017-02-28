package engine.asp

import core._
import core.asp.{AspFact, AspRule, NormalProgram, NormalRule}
import core.lars._

import scala.concurrent.duration._

/**
  * Created by fm on 20/01/2017.
  */
case class PlainLarsToAspMapper(engineTimeUnit: EngineTimeUnit = 1 second) extends LarsToAspMapper {

  def identityRulesForAtom(a: Atom): Seq[NormalRule] = {
    Seq(
      AspRule[Atom, Atom](a, Set(now.apply(T), PinnedAtom(a, T))),
      AspRule[Atom, Atom](PinnedAtom(a, T), Set(now(T), a))
    )
  }

  def encodingAtom(extendedAtom: ExtendedAtom): Atom = extendedAtom match {
    case AtAtom(t, a) => PinnedAtom(a, t)
    case a: Atom => a
    case a: WindowAtom => this.encodedWindowAtom(a)
  }

  // \window^1 @_T a(X)
  // head: w_{bla}(X,T)
  def slidingTime(window: SlidingTimeWindow, windowAtom: WindowAtom): WindowAtomEncoder = {
    val length = timePoints(window.windowSize.unit, window.windowSize.length)
    val head = encodedWindowAtom(windowAtom) //TODO beautify
    windowAtom.temporalModality match {
      case a: At => TimeAtEncoder(length, windowAtom.atom, head, a.time)
      case Diamond => TimeDiamondEncoder(length, windowAtom.atom, head)
      case Box => TimeBoxEncoder(length, windowAtom.atom, head)
    }
  }

  def slidingTuple(window: SlidingTupleWindow, windowAtom: WindowAtom): WindowAtomEncoder = {
    val head = encodedWindowAtom(windowAtom) //TODO beautify
    windowAtom.temporalModality match {
      case Diamond => TupleDiamondEncoder(window.windowSize, windowAtom.atom, head)
      case Box => TupleBoxEncoder(window.windowSize, windowAtom.atom, head)
      case a: At => TupleAtEncoder(window.windowSize, windowAtom.atom, head, a.time)
    }
  }

  def encodedWindowAtom(windowAtom: WindowAtom) = {
    val predicate = predicateFor(windowAtom)
    val previousArguments = windowAtom.atom match {
      case aa: AtomWithArgument => aa.arguments
      case a: Atom => Seq()
    }

    windowAtom.temporalModality match {
      case At(v: Time) => PinnedAtom(Atom(predicate, previousArguments), v)
      case _ => Atom(predicate, previousArguments)
    }
  }

  def timePoints(unit: TimeUnit, size: Long) = Duration(unit.toMillis(size) / engineTimeUnit.toMillis, engineTimeUnit.unit).length
}


object PlainLarsToAspMapper {
  def asNormalRule(rule: Rule[Atom, Atom]): NormalRule = AspRule(rule.head, rule.pos, rule.neg)

  def asNormalRules(rule: Rule[Atom, Atom]): Seq[NormalRule] = Seq(asNormalRule(rule))

  def asNormalRules(rules: Seq[Rule[Atom, Atom]]): Seq[NormalRule] = rules.map(asNormalRule)
}

//TODO hb review naming
/*
   at this point we have a representation for multiple evaluation modes:
   - for one-shot/reactive solving, everything is there by ruleEncodings plus the allWindowRules in windowAtomEncoders
   - for incremental solving, we use ruleEncodings + incrementalRulesAt (at every time point)

   b <- \window^1 \Diamond a

   b <- w           <-- this rule is contained in ruleEncodings
   w <- a(t-1)      <-- these rules are contained in allWindowRules, since they have a window atom representation in their head
   w <- a(0)
 */

case class TimeAtEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom, time: Time = T) extends TimeWindowEncoder {

  val parameter = time match {
    case tp: TimePoint => tp
    case _ => T // we want T as parameter so pinning is easy later on
  }

  // we need to unpack the windowAtomEncoding (from the PinnedAtom) in order to create a PinnedAtom(atom, T-k)
  private val unpackedWindowAtom = windowAtomEncoding.atom


  val allWindowRules = (0 to length.toInt) map (i => AspRule[Atom, Atom](PinnedAtom(unpackedWindowAtom, parameter - i), Set[Atom](now(parameter), PinnedAtom(atom, parameter - i))))

  val incrementalRule: NormalRule = AspRule[Atom, Atom](PinnedAtom(unpackedWindowAtom, T), Set[Atom](PinnedAtom(atom, T)))

  override def incrementalRulesAt(currentPosition: CurrentPosition): IncrementalRules = {
    val i = currentPosition.time
    val added = incrementalRule.assign(Assignment(Map(T -> i)))
    val removed = incrementalRule.assign(Assignment(Map(T -> IntValue(i.value.toInt - length.toInt)))) //TODO hb use map to outdate

    IncrementalRules(PlainLarsToAspMapper.asNormalRules(added), PlainLarsToAspMapper.asNormalRules(removed))
  }
}

/* EXAMPLE.
   b <- \window^range \Diamond a.
   ==>
   b <- w_{range-d-a}
   w_{range-d-a} <- now(N), a_at(T), T=N-0 //...range

   atom: Atom ... a
   windowAtomEncoding: w_{range-d-a}
 */
case class TimeDiamondEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom) extends TimeWindowEncoder {

  val allWindowRules = (0 to length.toInt) map (i => AspRule(windowAtomEncoding, Set[Atom](now(T), PinnedAtom(atom, T - i))))

  val incrementalRule: NormalRule = AspRule(windowAtomEncoding, Set[Atom](PinnedAtom(atom, T)))

  override def incrementalRulesAt(currentPosition: CurrentPosition): IncrementalRules = {
    val i = currentPosition.time
    val added = incrementalRule.assign(Assignment(Map(T -> i)))
    val removed = incrementalRule.assign(Assignment(Map(T -> IntValue(i.value.toInt - (length.toInt + 1)))))

    IncrementalRules(Seq(AspRule(added.head, added.pos)), Seq(AspRule(removed.head, removed.pos)))
  }
}


case class TimeBoxEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom) extends TimeWindowEncoder {

  val spoilerAtom = Atom(Predicate(f"spoil_te_${length}_${atom.predicate.caption}"), Atom.unapply(atom).getOrElse(Seq()))

  val baseRule: NormalRule = AspRule(windowAtomEncoding, Set(atom), Set(spoilerAtom))

  val spoilerRules: Seq[NormalRule] = (1 to length.toInt) map (i => AspRule(spoilerAtom, Set[Atom](atom, now(T)), Set[Atom](PinnedAtom(atom, T - i))))

  override val allWindowRules: Seq[NormalRule] = spoilerRules :+ baseRule

  val incrementalRule: NormalRule = AspRule(spoilerAtom, Set[Atom](atom), Set[Atom](PinnedAtom(atom, T)))

  override def incrementalRulesAt(currentPosition: CurrentPosition): IncrementalRules = {
    val time = currentPosition.time
    val added = incrementalRule.assign(Assignment(Map(T -> IntValue(time.value.toInt - 1))))
    val removed = incrementalRule.assign(Assignment(Map(T -> IntValue(time.value.toInt - (length.toInt + 1)))))

    // TODO: base rule is added every time - shouldn't matter because of set-semantics...
    IncrementalRules(PlainLarsToAspMapper.asNormalRules(added) :+ baseRule, PlainLarsToAspMapper.asNormalRules(removed))
  }
}

case class TupleDiamondEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom) extends TupleWindowEncoder {
  val C = Variable("C")

  val allWindowRules = 0 until length.toInt map (i => AspRule(windowAtomEncoding, Set[Atom](cnt(C), PinnedAtom.asCount(atom, C - i))))

  val incrementalRule: NormalRule = AspRule(windowAtomEncoding, Set[Atom](PinnedAtom.asCount(atom, C)))

  override def incrementalRulesAt(currentPosition: CurrentPosition): IncrementalRules = {
    val i = IntValue(currentPosition.count.toInt)

    val added = incrementalRule.assign(Assignment(Map( C -> i)))
    val removed = incrementalRule.assign(Assignment(Map(C -> IntValue(i.int - length.toInt))))

    IncrementalRules(Seq(AspRule(added.head, added.pos)), Seq(AspRule(removed.head, removed.pos)))
  }
}

case class TupleBoxEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom) extends TupleWindowEncoder {

  val C: Variable = Variable("C")
  val D: Variable = Variable("D")

  val D1: Variable = Variable("D1")
  val D2: Variable = Variable("D2")

  val T1: Variable = Variable("T1")
  val T2: Variable = Variable("T2")

  val spoilerAtom = Atom(Predicate(f"spoil_tu_${length}_${atom.predicate.caption}"), Atom.unapply(atom).getOrElse(Seq()))

  val baseRule: NormalRule = AspRule(windowAtomEncoding, Set(atom), Set(spoilerAtom))
  val cntSpoilerRules_1: Seq[NormalRule] = (1 to length.toInt) map { i =>
    AspRule(
      spoilerAtom,
      Set[Atom](
        atom,
        cnt(C)
      ),
      Set[Atom](
        PinnedAtom.asCount(atom, C - i)
      )
    )
  }

  val cntSpoilerRules_2: NormalRule = AspRule(
    spoilerAtom,
    Set[Atom](
      atom,
      cnt(C),
      PinnedAtom(atom, T1, D1),
      PinnedAtom(atom, T2, D2),
      Geq(D1, C - length.toInt),
      Eq(D1 + 1, D2),
      Gt(T2, T1 + 1)
    )
  )

  val spoilerRules: Seq[NormalRule] = cntSpoilerRules_1 :+ cntSpoilerRules_2

  override val allWindowRules: Seq[NormalRule] = spoilerRules :+ baseRule

  val incrementalSpoilerRule: NormalRule = AspRule(
    spoilerAtom,
    Set[Atom](
      atom
    ),
    Set[Atom](
      PinnedAtom.asCount(atom, D)
    )
  )
  val incrementalSpoilerRule2: NormalRule = AspRule(
    spoilerAtom,
    Set[Atom](
      atom,
      PinnedAtom(atom, T1, D1),
      PinnedAtom(atom, T2, D2),
      Geq(D1, C - length.toInt),
      Eq(D1 + 1, D2),
      Gt(T2, T1 + 1)
    )
  )

  val incrementalRules = Seq(incrementalSpoilerRule, incrementalSpoilerRule2)

  override def incrementalRulesAt(currentPosition: CurrentPosition): IncrementalRules = {
    val tick = IntValue(currentPosition.count.toInt)
    val added = incrementalRules.map(_.assign(Assignment(Map(C -> tick, D -> tick))))
    val removed = incrementalRules.map(_.assign(Assignment(Map(C -> tick, D -> IntValue(tick.int - length.toInt)))))

    IncrementalRules(PlainLarsToAspMapper.asNormalRules(added) :+ baseRule, PlainLarsToAspMapper.asNormalRules(removed))
  }
}

case class TupleAtEncoder(length: Long, atom: Atom, windowAtomEncoding: Atom, timeVariable: Time = T) extends TupleWindowEncoder {
  val D = Variable("D")
  val C = Variable("C")

  // at atoms got their parameter already encoded
  val allWindowRules = (0 to length.toInt) map (i => AspRule[Atom, Atom](windowAtomEncoding, Set[Atom](cnt(C), PinnedAtom(atom, timeVariable, D), Sum(D, IntValue(-i), D))))

  val incrementalRule: NormalRule = AspRule[Atom, Atom](windowAtomEncoding, Set[Atom](PinnedAtom(atom, timeVariable)))

  override def incrementalRulesAt(currentPosition: CurrentPosition): IncrementalRules = {
    null
  }
}