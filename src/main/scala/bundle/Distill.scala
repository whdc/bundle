package bundle

import collection.mutable.HashMap
import scala.math._

object Distill extends App {

  if( args.length != 1) {
    println( "Required argument: config-file")
    System.exit( 1)
  }
  val conf = Util.Conf.read( args(0))
  val dir = Util.getDir( args(0))
  val fn_base = Util.composePath( dir, conf("base"))
  val fn_data = Util.composePath( dir, conf("data"))
  val burnin = conf("burnin", 0) / conf("dumpfreq", 100)

  // parse data matrix
  val (langL, etymonN, xNL) = {
    val l_ = io.Source.fromFile( fn_data).getLines

    val langL = l_.next.split("\t").toSeq.tail
    val (etymonN, xNL) = l_.map{ line =>
      val f_ = line.split("\t")
      (f_.head, f_.tail.map(_.toInt).toSeq)
    }.toSeq.unzip
    (langL, etymonN, xNL)
  }
  val L = langL.length

  case class State(
    i: Int,
    iter: Int,
    zN: IndexedSeq[Int]
  ) {
    lazy val yNL: IndexedSeq[ IndexedSeq[ Int]] = {
      io.Source.fromFile( fn_base + ".y/" + iter + ".hex")
        .getLines().map { l => 
          val padded = l.map{ c => 
            Integer.toBinaryString( Integer.parseInt( ""+c, 16))
              .reverse.padTo( 4, '0')}.flatten
          padded.take( L).map( _ - '0').toIndexedSeq
        }.toIndexedSeq
    }
  }

  // read in samples of z__
  val fi = io.Source.fromFile( fn_base ++ ".z.log")
  def stateStream():Iterator[State] = 
    fi.reset().getLines().drop(1+burnin).zipWithIndex
      .map{ case (s, i) =>
        val l = s.split( "\t").map( _.toInt).toIndexedSeq
        State( i, l.head, {
          val zN = l.tail
          val rank = Map( Set( zN:_*).toIndexedSeq.sorted.zipWithIndex:_*)
          zN.map( rank( _))
        })
      }

  // figure out K & I
  var I = 0
  var K = 0
  var N = 0
  for( s <- stateStream()) {
    I += 1
    K = max( K, s.zN.max+1)
    N = s.zN.length
  }

  println( "samples after burnin = %d, K = %d, N = %d, L = %d".format( I, K, N, L))

  // permutation for each sample
  val π__ = Array.fill( I, K)( 0)

  // counts for each cell
  val c__ = Array.fill( N, K)( 0)

  def update( z_ : IndexedSeq[Int], π_ : IndexedSeq[ Int], mode : Int) : Unit = {
    for( n <- 0 until N) {
      c__( n)( π_( z_( n))) += mode
    }
  }

  // flog for speed
  def pairScores( z_ : IndexedSeq[ Int]) : Array[ Array[ Double]] = {
    val s__ = Array.fill( K, K)( 0.)
    var n = 0
    while( n < N) {
      val c_ = c__( n)
      val z = z_( n)
      var k = 0
      while( k < K) {
        s__( z)( k) -= c_( k)
        k += 1
      }
      n += 1
    }
    s__
  }

  // flog for speed
  def score() : Long = {
    var s: Long = 0
    var n = 0
    while( n < N) {
      val c_ = c__( n)
      var k = 0
      while( k < K) {
        val c = c_( k)
        s += c * (c-1) / 2
        k += 1
      }
      n += 1
    }
    s
  }

  // wrapper for bipartite matching algorithm
  def bmatch( s__ : Array[ Array[ Double]]) : Seq[ Int] = {
    val t__ = Seq.tabulate( s__.length, s__(0).length)( (i,j) => s__(i)(j))
    breeze.optimize.linear.KuhnMunkres.extractMatching( t__)._1
  }

  // initial round
  println( "Aligning z samples...")
  (0 until K).copyToArray( π__( 0))
  for( state <- stateStream()) {
    val z_ = state.zN
    bmatch( pairScores( z_)).copyToArray( π__( state.i))
    update( z_, π__( state.i), 1)
  }

  // later rounds
  var old_score = 0L
  for( j <- 1 to 20) {
    if( old_score < score()) {
      println( "Round "+j+" score: " + score())
      old_score = score()
      for( state <- stateStream()) {
        val z_ = state.zN
        update( z_, π__( state.i), -1)
        bmatch( pairScores( z_)).copyToArray( π__( state.i))
        update( z_, π__( state.i), 1)
      }
    }
  }

  val kCount_ = Array.tabulate( K)( 
    k => c__.map( _( k)).sum)

  val kRRank_ = kCount_.zipWithIndex.sortBy( -_._1).map( _._2)

  val kRank_ = kRRank_.zipWithIndex.sortBy( _._1).map( _._2)

  /*
  for( k8 <- 0.until( K, 8)) {
    for( k <- k8 until (k8+8)) {
      if( k < K) print( "%7d " format kCount_( kRRank_( k)))
    }
    println()
  }
  */

  println( "Distilling...")

  /*
  val fo = new java.io.FileWriter( fn_base + ".za.log")
  for( state <- stateStream()) {
    val z_ = state.zN
    fo.write( "" + state.iter + "\t" + state.zN.map( 
      k => "%d" format kRank_( π__( state.i)( k))).mkString("\t") + "\n")
  }
  fo.close()
  */
  
  // cluster/language counts
  val c___ = Array.fill( K, L, 2)( 0)

  // cluster/etymon counts
  val d__ = Array.fill( K, N)( 0)

  for( state <- stateStream()) {
    val zN = state.zN.map( k => kRank_( π__( state.i)( k)))
    val yNL = state.yNL
    for( n <- 0 until N) {
      val z = zN( n)
      val yL = yNL( n)
      val xL = xNL( n)
      d__( z)( n) += 1
      for( l <- 0 until L)
        if( yL( l) == 1) {
          c___( z)( l)( xL( l)) += 1
        }
    }
  }

  val foez = new java.io.FileWriter( fn_base + ".ez.tsv")
  foez.write( etymonN.mkString("\t") + "\n")
  for( k <- 0 until K)
    foez.write( d__( k).map( d => "%.4f".format( d/I.toDouble)).mkString("\t") ++ "\n")
  foez.close()

  val foth = new java.io.FileWriter( fn_base + ".th.tsv")
  foth.write( langL.mkString("\t") + "\n")
  for( k <- 0 until K)
    foth.write( c___( k).map( c_ => "%.4f".format( c_(1).toDouble / (c_(1) + c_(0))))
      .mkString("\t") ++ "\n")
  foth.close()

  // cluster stats
  val softSizeK = d__.map( _.sum)
  val hardSizeK = d__.map( _.count( _ > 0.5))

  // display clusters
  println( "\n%d CLUSTERS WITH 2+ HARD MEMBERS\n" format hardSizeK.count( _ >= 2))
  for( line <- langL.map( _.take(3).padTo(3, ' ')).transpose)
    println( "     " + line.mkString.grouped(5).mkString(" "))
  for( k <- 0 until K) {
    println( "%4d ".format( hardSizeK(k)) + c___( k).map { c_ =>
      val th = c_(1).toDouble / (c_(0) + c_(1))
      " .:=@"(min(4, (th * 5).toInt))
    }.mkString.grouped(5).mkString(" "))
  }
}
