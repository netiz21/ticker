package core.lars

/**
  * Created by FM on 01.05.16.
  */
trait WindowFunction


case class SlidingTimeWindow(windowSize: WindowSize) extends WindowFunction