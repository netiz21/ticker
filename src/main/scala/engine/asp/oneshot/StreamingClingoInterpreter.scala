package engine.asp.oneshot

import clingo.{ClingoConversion, ClingoProgram, _}
import core._
import core.lars.TimePoint
import engine.asp.{PinnedModel, PinnedStream}


/**
  *
  * Created by FM on 22.04.16.
  */
case class StreamingClingoInterpreter(program: ClingoProgram, clingoEvaluation: ClingoEvaluation = ClingoEvaluation()) extends StreamingAspInterpreter {

  def apply(timePoint: TimePoint, pinnedAtoms: PinnedStream): Option[PinnedModel] = {

    val transformed = pinnedAtoms map (ClingoConversion(_))

    val aspResult = clingoEvaluation(program ++ transformed).headOption

    aspResult match {
      case Some(model) => Some(StreamingClingoInterpreter.asPinnedAtom(model, timePoint))
      case None => None
    }
  }
}

object StreamingClingoInterpreter {
  def asPinnedAtom(model: Model, timePoint: TimePoint) = model map {
    case aa: AtomWithArgument => convertToPinnedAtom(aa, timePoint)
    case a: Atom => throw new IllegalArgumentException(f"Cannot convert '$a' into a PinnedAtom")
  }

  val numberFormat = """\d+""".r

  def convertToPinnedAtom(atom: AtomWithArgument, timePoint: TimePoint): PinnedAtom = atom.arguments.last match {
    case Value(v) => convertValue(atom, v)
    case _ => throw new IllegalArgumentException("Can only handle values as last argument")
  }

  def convertValue(atom: AtomWithArgument, value: String): PinnedAtom = numberFormat.findFirstIn(value) match {
    case Some(number) => {
      val l = number.toLong

      val atomWithoutTime = atom.arguments.init match {
        case Nil => atom.atom
        case remainingArguments => NonGroundAtomWithArguments(atom.atom, remainingArguments)
      }

      PinnedAtom(atomWithoutTime, l)
    }
    case None => throw new IllegalArgumentException(f"Cannot convert '$value' into a TimePoint for a PinnedAtom")
  }

}
