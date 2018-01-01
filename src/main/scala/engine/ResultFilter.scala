package engine

import core._
import core.lars.TimePoint

/**
  * Created by fm on 05/06/2017.
  */

case class EngineWithFilter(evaluationEngine: Engine, filter: AtomResultFilter) extends Engine {

  override def append(time: TimePoint)(atoms: Atom*): Unit = evaluationEngine.append(time)(atoms: _*)

  override def evaluate(time: TimePoint): Result = filter.filter(time, evaluationEngine.evaluate(time))
}

//case class EvaluationEngineWithConversion (evaluationEngine: EvaluationEngine)extends EvaluationEngine{
//
//}

case class AtomResultFilter(restrictTo: Set[Atom]) {

  val restrictToPredicates = restrictTo.map(_.predicate)

  val fixedAuxiliaryAtomPredicates = asp.specialPinPredicates.toSet

  def filter(timePoint: TimePoint, result: Result): Result = {
    result.get match {
      case Some(model) => {

        val withoutAuxiliary = model filterNot { a => fixedAuxiliaryAtomPredicates.contains(a.predicate) }

        val filteredAfterTime = withoutAuxiliary collect {
          case GroundPinnedAtAtom(atom, t) if t == timePoint => convertAtom(atom)
          case g: GroundAtom => g
        }

        val restrictedOnly = filteredAfterTime filter { a => restrictToPredicates.contains(a.predicate) }

        Result(restrictedOnly)
      }
      case None => EmptyResult
    }
  }

  def filterToPredicates(model: Model, predicates: Set[Predicate]) = {
    model filterNot { a => predicates.contains(a.predicate) }
  }

  private val TimeAtomPattern = "(.+)_at".r
  //private val CntAtomPattern = "(.+)_cnt".r
  private val TimeCntAtomPattern = "(.+)_at_cnt".r

  def convertAtom(atom: Atom): Atom = {
    val arguments = Atom.unapply(atom).getOrElse(Seq())

    val predicateName = atom.predicate.caption match {
      case TimeCntAtomPattern(predicate) => predicate
      case TimeAtomPattern(predicate) => predicate
      case _ => atom.predicate.caption
    }

    Atom(Predicate(predicateName), arguments)
  }
}