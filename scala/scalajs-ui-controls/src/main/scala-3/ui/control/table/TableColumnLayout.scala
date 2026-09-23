package ui.control.table

/** Pure width calculation shared by layout, pointer gestures and the imperative API. */
private[table] object TableColumnLayout {
  private val epsilon = 0.000001

  final case class Column(min: Double, preferred: Double, max: Double, resizable: Boolean) {
    val minimum: Double              = if (min.isFinite) math.max(0, min) else 40.0
    val maximum: Double              = math.max(minimum, if (max.isFinite) max else Double.MaxValue)
    def clamp(width: Double): Double = math.max(minimum, math.min(maximum, width))
    val initial: Double              = clamp(if (preferred.isFinite) preferred else 160.0)
  }

  def layout(
      columns: Vector[Column],
      viewport: Double,
      policy: ColumnResizePolicy
  ): Vector[Double] = {
    val widths = columns.map(_.initial).toArray
    if (policy != ColumnResizePolicy.Unconstrained && viewport.isFinite)
      distribute(columns, widths, columns.indices.toVector, math.max(0, viewport) - widths.sum)
    widths.toVector
  }

  /** A single column's resize, by a pixel delta. */
  def resize(
      columns: Vector[Column],
      widths: Vector[Double],
      index: Int,
      delta: Double,
      policy: ColumnResizePolicy
  ): Vector[Double] = resizeGroup(columns, widths, Vector(index), delta, policy)

  /** A group's resize: `indices` are every visible leaf the dragged handle actually belongs to --
    * one column for an ordinary leaf resize (`resize` above), every visible leaf of a group for an
    * actual group resize (C05). The group's own children share `delta` proportionally to their
    * current width, exactly as `AllColumns`/`SubsequentColumns` already share a delta across
    * several recipients; what they collectively cannot absorb is what a single-leaf resize would
    * call `requested`, and everything past that point -- capacity, `applied`, compensating
    * recipients outside `indices`, and the final write-back to `indices` themselves -- is identical
    * to the single-column case, just phrased over a set instead of one index. A single-index call
    * degenerates to exactly the previous single-column algorithm: water-filling one recipient
    * converges to its plain clamp in one step. A separate name from `resize`, not an overload of
    * it: an overload sharing this position with a bare `Int` one made every untyped `Vector(...)`
    * literal in this file's own tests ambiguous between the two.
    */
  def resizeGroup(
      columns: Vector[Column],
      widths: Vector[Double],
      indices: Vector[Int],
      delta: Double,
      policy: ColumnResizePolicy
  ): Vector[Double] = {
    if (
      indices.isEmpty || !delta.isFinite ||
      indices.exists(i => i < 0 || i >= columns.size) ||
      indices.forall(i => !columns(i).resizable)
    ) return widths
    val result = widths.toArray
    if (policy == ColumnResizePolicy.Unconstrained)
      distribute(columns, result, indices, delta, proportionalToWidth = true)
    else {
      // Dry run on a scratch copy: how much would `indices` move on their own, unconstrained by
      // what neighbors can actually give up? That candidate -- not `delta` itself -- is what
      // neighbors are asked to compensate below.
      val requested =
        distribute(columns, widths.toArray, indices, delta, proportionalToWidth = true)
      val boundary   = indices.max
      val after      = ((boundary + 1) until columns.size).toVector
      val recipients = policy match {
        case ColumnResizePolicy.AllColumns => columns.indices.filterNot(indices.contains).toVector
        case ColumnResizePolicy.NextColumn => after.take(1)
        case ColumnResizePolicy.LastColumn => after.takeRight(1)
        case ColumnResizePolicy.FlexLastColumn => after.reverse
        case _                                 => after
      }
      val active   = recipients.filter(columns(_).resizable)
      val capacity = active.map { i =>
        if (requested >= 0) widths(i) - columns(i).minimum else columns(i).maximum - widths(i)
      }.sum
      val applied      = math.signum(requested) * math.min(math.abs(requested), capacity)
      val proportional = policy == ColumnResizePolicy.AllColumns ||
        policy == ColumnResizePolicy.SubsequentColumns
      val consumed =
        if (proportional) distribute(columns, result, active, -applied, proportionalToWidth = true)
        else {
          var remaining = -applied
          active.foreach { i =>
            val next = columns(i).clamp(result(i) + remaining)
            remaining -= next - result(i)
            result(i) = next
          }
          -applied - remaining
        }
      // What neighbors actually gave up -- possibly less than `requested` -- is what `indices`
      // finally get, the same way a single column's result is clamp(widths(index) - consumed).
      distribute(columns, result, indices, -consumed, proportionalToWidth = true)
    }
    result.toVector
  }

  /** Runs a [[CustomColumnResizePolicy]] in place of a built-in strategy. A malformed result --
    * wrong length -- is rejected wholesale, the same atomic-or-nothing contract `resize` already
    * gives the built-in policies; each accepted width is still clamped to its own column's bounds,
    * so a policy cannot violate them by construction.
    */
  private[table] def applyCustom(
      columns: Vector[Column],
      widths: Vector[Double],
      viewport: Double,
      target: Option[(Vector[Int], Double)],
      policy: CustomColumnResizePolicy
  ): Vector[Double] = {
    val request = ColumnResizeRequest(
      columns.map(c => ColumnResizeSpec(c.minimum, c.maximum, c.initial, c.resizable)),
      widths,
      viewport,
      target
    )
    val result = policy(request)
    if (result.size != columns.size) widths
    else columns.zip(result).map((c, w) => c.clamp(if (w.isFinite) w else c.initial))
  }

  /** Water filling: a saturated column leaves the set; no fixed iteration cap or lost remainder. */
  private def distribute(
      columns: Vector[Column],
      widths: Array[Double],
      indices: Vector[Int],
      delta: Double,
      proportionalToWidth: Boolean = false
  ): Double = {
    var remaining = delta
    var active    = indices.filter(columns(_).resizable)
    while (active.nonEmpty && math.abs(remaining) > epsilon) {
      active = active.filter { i =>
        if (remaining > 0) widths(i) < columns(i).maximum - epsilon
        else widths(i) > columns(i).minimum + epsilon
      }
      val weights = active.map(i =>
        math.max(1.0, if (proportionalToWidth) widths(i) else widths(i) - columns(i).minimum)
      )
      val total    = weights.sum
      var consumed = 0.0
      active.zip(weights).foreach { (i, weight) =>
        val next = columns(i).clamp(widths(i) + remaining * (weight / total))
        consumed += next - widths(i)
        widths(i) = next
      }
      if (math.abs(consumed) <= epsilon) active = Vector.empty
      remaining -= consumed
    }
    delta - remaining
  }
}
