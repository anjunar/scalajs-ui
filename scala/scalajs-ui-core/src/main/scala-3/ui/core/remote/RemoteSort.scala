package ui.core.remote

final case class RemoteSort(field: String, ascending: Boolean = true) {
  def direction: String    = if (ascending) "asc" else "desc"
  def asQueryValue: String = s"$field,$direction"
}
