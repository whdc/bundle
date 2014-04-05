package bundle

object Util {

  class Conf( fn: String) {
    var section = ""
    val m = (for( l <- io.Source.fromFile( fn).getLines) yield {
      val l2 = l.replaceAll( """\#.*""", "").trim
      if( l2 == "") {
        None
      } else if( l2(0) == '[') {
        section = l2.slice( 1, l2.length-2) + "."
        None
      } else if( l2.split( """\s+""").length >= 2) {
        val Array(k, v) = l2.split( """\s+""", 2)
        Option( (section+k, v))
      } else {
        println( s"In [$fn], ignoring:")
        println( l)
        None
      }
    }).flatten.toMap

    def apply( k: String, v: String = null) = {
      if( m contains k) {
        m( k)
      } else if( v != null) {
        v
      } else {
        println( s"In [$fn], need key [$k]")
        System.exit( 1)
        ""
      }
    }

    def apply( k: String, v: Double) = {
      if( m contains k) {
        m( k).toDouble
      } else {
        v
      }
    }

    def apply( k: String, v: Int) = {
      if( m contains k) {
        m( k).toInt
      } else {
        v
      }
    }
  }

  object Conf {
    def read( fn: String) = new Conf( fn)
  }

  // get elements leading to filename, without the slash
  def getDir( path: String) = {
    val i = path.lastIndexOf( '/')
    if( i == -1) "." else path.slice( 0, i)
  }

  def composePath( dir: String, fn: String) = {
    if( fn(0) == '~' || fn(0) == '/') fn else dir + '/' + fn
  }
}
