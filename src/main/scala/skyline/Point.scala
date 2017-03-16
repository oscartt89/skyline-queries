package skyline

class Point(d: Array[Int]) extends Ordered[Point]{
	val data = d

	def dominates(q: Point): Boolean = {
		var i: Int = 0
		var res: Boolean = true
		while(res && i < data.length && i < q.data.length) {
			if(data(i) > q.data(i)) {
				res = false
			}
			i = i + 1
		}

		res = res && (data.length == q.data.length)
		res
	}

	override def toString(): String = {
		data.mkString(" ")
	}

	override def equals(that: Any): Boolean =
    that match {
      case that: Point => this.data.deep == that.data.deep
      case _ => false
   	}

   	def compare(that: Point): Int = {
   		var i = 0
   		var res = 0
   		while(res == 0 && i < data.length && i < that.data.length) {
   			if(data(i) < that.data(i)){
   				res = -1
   			} else if (data(i) > that.data(i)){
   				res = 1
   			}
   			i = i + 1
   		}
   		res
   	}
}

object Point {
	def apply(d: Array[Int]) = new Point(d)
}