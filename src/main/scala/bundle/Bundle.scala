package linkage

import scala.collection.mutable.ArrayBuffer
import scala.math._
import breeze.numerics.lgamma
import breeze.stats.distributions.Rand

object Continuum extends App {

  if( args.length != 1) {
    println( "Required argument: config-file")
    System.exit( 1)
  }
  val conf = Util.Conf.read( args(0))
  val dir = Util.getDir( args(0))
  val fn_base = Util.composePath( dir, conf("base"))
  val fn_data = Util.composePath( dir, conf("data"))

  val move_alpha = conf("move.alpha", "yes") == "yes"
  val move_rhomu = conf("move.rhomu", "yes") == "yes"
  val move_rholam = conf("move.rholam", "yes") == "yes"
  val move_zetalam = conf("move.zetalam", "yes") == "yes"
  val move_smIter = conf("move.smiter", 0).toInt

  // set up info
  val foInfo = new java.io.FileWriter( fn_base + ".out")
  def info( s: String) {
    print( s)
    foInfo.write( s)
  }

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

  // dimensions
  val L = langL.length
  val N = etymonN.length
  var K = 1

  // set up trace files
  val foLog = new java.io.FileWriter( fn_base + ".log")
  val columns = Seq( "iter", "ll", "llx", "lly", "llz", "K") ++ 
    Seq( "alpha", "rhomu", "rholam", "zetalam") ++ langL
  foLog.write( columns.mkString("\t") + "\n")

  var foz = new java.io.FileWriter( fn_base + ".z.log")
  foz.write( "iter\t" + etymonN.mkString("\t") + "\n")

  // quantities to resample
  var α = conf("init.alpha", 5.).toDouble
  var ρμ = conf("init.rhomu", 0.5).toDouble
  var ρλ = conf("init.rholam", 0.5).toDouble
  val zN = ArrayBuffer.fill( N)( 0)

  val numEtymaL = IndexedSeq.tabulate( L)( l => xNL.map( _( l)).sum)
  val maxEtyma = numEtymaL.max
  val ζμL = numEtymaL.map( _.toDouble / maxEtyma * 0.9)
  var ζλ = conf("init.rholam", 10.).toDouble

  val yNL = ArrayBuffer.fill( N, L)( 1)

  info(
"""
L: %1d, number of languages.
N: %1d, number of etyma.
α: %8.4f, DP concentration, resampled: %s.
ρμ: %8.4f, cluster/language intensity freq prior, resampled: %s.
ρλ: %8.4f, cluster/language intensity freq prior, resampled: %s.
ζμ: observability, set proportional to vocabulary size.
ζλ: %8.4f, observability prior, resample: %s.
smIter: %d rounds of split-merge on z.
""".format(
    L, N, α, move_alpha,
    ρμ, move_rhomu, 
    ρλ, move_rholam, 
    ζλ, move_zetalam, 
    move_smIter))

  // counts
  val mK = ArrayBuffer.fill( N)( 0)
  val hL = ArrayBuffer.fill( L)( N)
  val mKL1 = ArrayBuffer.fill( N, L)( 0)
  val mKL0 = ArrayBuffer.fill( N, L)( 0)
  mK( 0) = N
  for( l <- 0 until L) {
    mKL1( 0)( l) = xNL.map( _( l)).sum
    mKL0( 0)( l) = N - mKL1( 0)( l)
  }

  // temp storage
  val t0 = ArrayBuffer.fill( N+1)( 0)
  val t1 = ArrayBuffer.fill( N+1)( 0.)

  for( iter <- 0 until conf("maxiter",10000)) {

    // resample each yNL
    for( n <- 0 until N; val k = zN( n); l <- 0 until L; if xNL( n)( l) == 0) {
      val y = yNL( n)( l)
      val ll1 = (ζμL( l) * ζλ + hL( l) - y) / (ζλ + N - 1) * 
                (ρμ * ρλ + mKL0( k)( l) - y) / (ρλ + mKL1( k)( l) + mKL0( k)( l) - y)
      val ll0 = ((1. - ζμL( l)) * ζλ + N - 1 - hL( l) + y) / (ζλ + N - 1)
      val p = if( y == 1) ll0/ll1 else ll1/ll0
      if( accept( p)) {
        if( y == 1) {
          yNL( n)( l) = 0
          hL( l) -= 1
          mKL0( k)( l) -= 1
        } else {
          yNL( n)( l) = 1
          hL( l) += 1
          mKL0( k)( l) += 1
        }
      }
    }

    // resample each zN
    for( n <- 0 until N) {
      val z = zN( n)
      mK( z) -= 1
      for( l <- 0 until L) {
        mKL1( z)( l) -= xNL( n)( l)
        mKL0( z)( l) -= yNL( n)( l) * (1 - xNL( n)( l))
      }
      if( mK( z) == 0) K -= 1

      // set up Gibbs
      var k = 0
      var kk = 0
      while( kk < K) {
        if( mK( k) > 0) {
          t0( kk) = k
          t1( kk) = mK( k) / (N - 1 + α)
          kk += 1
        }
        k += 1
      }
      t0( K) = mK.indexOf( 0)
      t1( K) = α / (N - 1 + α)
      for( kk <- 0 to K) {
        val k = t0( kk)
        val m = mK( k)
        val mL1 = mKL1( k)
        val mL0 = mKL0( k)
        val xL = xNL( n)
        val yL = yNL( n)
        for( l <- 0 until L) {
          if( yL( l) == 1)
            t1( kk) *=
              (if( xL( l) == 1) ρμ * ρλ + mL1( l) else (1. - ρμ) * ρλ + mL0( l)) /
              (ρλ + mL1( l) + mL0( l))
        }
      }
      // new pick for k
      k = t0( pick( t1, K+1))
      if( mK( k) == 0) K += 1
      zN( n) = k
      mK( k) += 1
      for( l <- 0 until L) {
        mKL1( k)( l) += xNL( n)( l)
        mKL0( k)( l) += yNL( n)( l) * (1 - xNL( n)( l))
      }
    }

    // split-merge (Dahl 2003)
    var om1 = 0
    var om2 = 0
    for( i <- 0 until move_smIter) {
      val n1 = Rand.randInt( N).get()
      val temp = Rand.randInt( N-1).get()
      val n2 = if( temp >= n1) temp + 1 else temp
      if( zN( n1) == zN( n2)) { // split
        val k = zN( n1)

        // Must calculate three things:
        // (1) proposal, stored in t1
        // (2) lhas: log of Hastings ratio
        // (3) lmet: log of Metropolis ratio

        t1( n1) = 1
        t1( n2) = 2
        var lhas = 0.

        // counts for first partition
        var m1 = 1
        val m1L1 = ArrayBuffer.tabulate( L)( l => yNL( n1)( l) * xNL( n1)( l))
        val m1L0 = ArrayBuffer.tabulate( L)( l => yNL( n1)( l) * (1 - xNL( n1)( l)))
        // counts for second partition
        var m2 = 1
        val m2L1 = ArrayBuffer.tabulate( L)( l => yNL( n2)( l) * xNL( n2)( l))
        val m2L0 = ArrayBuffer.tabulate( L)( l => yNL( n2)( l) * (1 - xNL( n2)( l)))

        val cluster = (0 until N).filter( zN( _) == k)
        val order = Rand.permutation( mK( k)).get().map( cluster( _))
        for( n <- order; if n != n1 && n != n2) {
          val xL = xNL( n)
          var mass1 = m1.toDouble
          var mass2 = m2.toDouble
          for( l <- 0 until L; if yNL( n)( l) == 1) {
            mass1 *=
              (if( xL( l) == 1) ρμ * ρλ + m1L1( l) 
               else (1. - ρμ) * ρλ + m1L0( l)) /
              (ρλ + m1L1( l) + m1L0( l))
            mass2 *=
              (if( xL( l) == 1) ρμ * ρλ + m2L1( l) 
               else (1. - ρμ) * ρλ + m2L0( l)) /
              (ρλ + m2L1( l) + m2L0( l))
          }
          if( unif() * (mass1 + mass2) < mass1) {
            m1 += 1
            for( l <- 0 until L; if yNL( n)( l) == 1) {
              if( xL( l) == 1) m1L1( l) += 1
              else m1L0( l) += 1
            }
            t1( n) = 1  // use t1 to store proposal
            lhas += log( mass1 / (mass1 + mass2))
          } else {
            m2 += 1
            for( l <- 0 until L; if yNL( n)( l) == 1) {
              if( xL( l) == 1) m2L1( l) += 1
              else m2L0( l) += 1
            }
            t1( n) = 2  // use t1 to store proposal
            lhas += log( mass2 / (mass1 + mass2))
          }
        }

        var lmet1 = 0.
        for( l <- 0 until L) {
          lmet1 += logPolya2D( m1L1( l), m1L0( l), ρμ * ρλ, (1. - ρμ) * ρλ)
          lmet1 += logPolya2D( m2L1( l), m2L0( l), ρμ * ρλ, (1. - ρμ) * ρλ)
          lmet1 -= logPolya2D( mKL1( k)( l), mKL0( k)( l), ρμ * ρλ, (1. - ρμ) * ρλ)
          /*
          assert( mKL1( k)( l) == m1L1( l) + m2L1( l), {
            println( mKL1( k)( l), m1L1( l), m2L1( l))
          })
          assert( mKL0( k)( l) == m1L0( l) + m2L0( l), {
            println( mKL0( k)( l), m1L0( l), m2L0( l))
          })
          */
        }
        val lmet2 = lgamma( m1) + lgamma( m2) - lgamma( mK( k))

        if( laccept( lmet1 + lmet2 - lhas)) {
          K += 1
          val k2 = mK.indexOf( 0)
          for( l <- 0 until L) {
            mKL1( k)( l) = m1L1( l)
            mKL0( k)( l) = m1L0( l)
            mKL1( k2)( l) = m2L1( l)
            mKL0( k2)( l) = m2L0( l)
          }
          mK( k) = m1
          mK( k2) = m2
          for( n <- order; if t1( n) == 2) zN( n) = k2
          if( m1 == om1 && m2 == om2 || m1 == om2 && m2 == om1) {
            info( "*** WARNING: POSSIBLE RESPLIT\n")
          }
          om1 = m1
          om2 = m2
          info( "*** SPLIT %4d %4d [lmet %10.4f %10.4f] [lhas %10.4f]\n"
            .format( m1, m2, lmet1, lmet2, lhas))
        }
      } else { // merge
        val k1 = zN( n1)
        val k2 = zN( n2)

        // Must calculate two things:
        // (1) lhas: log of Hastings ratio
        // (2) lmet: log of Metropolis ratio

        var lhas = 0.

        // counts for first partition
        var m1 = 1
        val m1L1 = ArrayBuffer.tabulate( L)( l => yNL( n1)( l) * xNL( n1)( l))
        val m1L0 = ArrayBuffer.tabulate( L)( l => yNL( n1)( l) * (1 - xNL( n1)( l)))
        // counts for second partition
        var m2 = 1
        val m2L1 = ArrayBuffer.tabulate( L)( l => yNL( n2)( l) * xNL( n2)( l))
        val m2L0 = ArrayBuffer.tabulate( L)( l => yNL( n2)( l) * (1 - xNL( n2)( l)))

        val cluster = (0 until N).filter( n => zN( n) == k1 || zN( n) == k2)
        val order = Rand.permutation( mK( k1) + mK( k2)).get().map( cluster( _))
        for( n <- order; if n != n1 && n != n2) {
          val xL = xNL( n)
          var mass1 = m1.toDouble
          var mass2 = m2.toDouble
          for( l <- 0 until L; if yNL( n)( l) == 1) {
            mass1 *=
              (if( xL( l) == 1) ρμ * ρλ + m1L1( l) 
               else (1. - ρμ) * ρλ + m1L0( l)) /
              (ρλ + m1L1( l) + m1L0( l))
            mass2 *=
              (if( xL( l) == 1) ρμ * ρλ + m2L1( l) 
               else (1. - ρμ) * ρλ + m2L0( l)) /
              (ρλ + m2L1( l) + m2L0( l))
          }
          if( zN( n) == k1) {
            m1 += 1
            for( l <- 0 until L; if yNL( n)( l) == 1) {
              if( xL( l) == 1) m1L1( l) += 1
              else m1L0( l) += 1
            }
            lhas -= log( mass1 / (mass1 + mass2))
          } else {
            m2 += 1
            for( l <- 0 until L; if yNL( n)( l) == 1) {
              if( xL( l) == 1) m2L1( l) += 1
              else m2L0( l) += 1
            }
            lhas -= log( mass2 / (mass1 + mass2))
          }
        }

        var lmet1 = 0.
        for( l <- 0 until L) {
          lmet1 -= logPolya2D( m1L1( l), m1L0( l), ρμ * ρλ, (1. - ρμ) * ρλ)
          lmet1 -= logPolya2D( m2L1( l), m2L0( l), ρμ * ρλ, (1. - ρμ) * ρλ)
          lmet1 += logPolya2D( m1L1( l) + m2L1( l), m1L0( l) + m2L0( l), 
            ρμ * ρλ, (1. - ρμ) * ρλ)
        }
        val lmet2 = -(lgamma( m1) + lgamma( m2) - lgamma( m1 + m2))

        if( laccept( lmet1 + lmet2 - lhas)) {
          K -= 1
          // retain the smaller of k1, k2
          val (t1, t2) = if( k1 < k2) (k1, k2) else (k2, k1)
          for( l <- 0 until L) {
            mKL1( t1)( l) += mKL1( t2)( l)
            mKL0( t1)( l) += mKL0( t2)( l)
            mKL1( t2)( l) = 0
            mKL0( t2)( l) = 0
          }
          mK( t1) += mK( t2)
          mK( t2) = 0
          for( n <- order) zN( n) = t1
          if( m1 == om1 && m2 == om2 || m1 == om2 && m2 == om1) {
            info( "*** WARNING: POSSIBLE REMERGE\n")
          }
          om1 = m1
          om2 = m2
          info( "*** MERGE %4d %4d [lmet %10.4f %10.4f] [lhas %10.4f]\n"
            .format( m1, m2, lmet1, lmet2, lhas))
        }
      }
    }

    // resample ρ1, ρ0
    var ρμn = ρμ
    var ρλn = ρλ
    if( move_rhomu) {
      val scale = exp( -1 + -10. * unif())
      ρμn = 1. - (1. - (ρμ + scale * (unif() - 0.5)).abs).abs
    } 
    if( move_rholam) {
      val scale = exp( -10. * unif())
      ρλn = (ρλ + scale * (unif() - 0.5)).abs
    }
    if( move_rhomu || move_rholam) {
      var ll = 0.
      var k = 0
      var kk = 0
      while( kk < K) {
        val m = mK( k)
        val mL1 = mKL1( k)
        val mL0 = mKL0( k)
        if( m > 0) {
          for( l <- 0 until L)
            ll += logPolya2D( mL1( l), mL0( l), ρμn * ρλn, (1. - ρμn) * ρλn) -
                  logPolya2D( mL1( l), mL0( l), ρμ * ρλ, (1. - ρμ) * ρλ)
          kk += 1
        }
        k += 1
      }
      if( laccept( ll)) {
        ρμ = ρμn
        ρλ = ρλn
      }
    }

    // resample ζ
    if( move_zetalam) {
      val scale = exp( -10. * unif())
      val ζλn = (ζλ + scale * (unif() - 0.5)).abs
      var ll = 0.
      for( l <- 0 until L) {
        val ζμ = ζμL( l)
        ll += logPolya2D( hL( l), N - hL( l), ζμ * ζλn, (1. - ζμ) * ζλn) -
              logPolya2D( hL( l), N - hL( l), ζμ * ζλ, (1. - ζμ) * ζλ)
      }
      if( laccept( ll)) ζλ = ζλn
    }

    // resample α
    if( move_alpha) {
      val αn = (α + unif() * 0.1 - 0.05).abs
      val ll = 
       (lgamma( αn) + log( αn) * K - lgamma( N + αn)) -
       (lgamma( α) + log( α) * K - lgamma( N + α))
      if( laccept( ll)) α = αn
    }

    if( iter % conf( "infofreq", 10) == 0) {
      info( "--- %d ---\n" format iter)
      info( "[α %8.4f] [ρμ %8.4f ρλ %8.4f] [ζλ %8.4f] [K %3d]\n".format( 
        α, ρμ, ρλ, ζλ, K))

      info( mK.filter( _ != 0).sorted.reverse.map( _.toString).mkString( " ") ++ "\n")

      // calculate log likelihood
      var llz = lgamma( α) + log( α) * K - lgamma( N + α)
      for( k <- 0 until N; if mK( k) > 0) llz += lgamma( mK( k))
      assert( mK.sum == N)
      assert( mK.filter( _ > 0).length == K)
      var lly = 0.
      for( l <- 0 until L) {
        val ζμ = ζμL( l)
        lly += logPolya2D( hL( l), N - hL( l), ζμ * ζλ, (1. - ζμ) * ζλ)
      }
      var llx = 0.
      ; {
        var k = 0
        var kk = 0
        while( kk < K) {
          val m = mK( k)
          val mL1 = mKL1( k)
          val mL0 = mKL0( k)
          if( m > 0) {
            for( l <- 0 until L)
              llx += logPolya2D( mL1( l), mL0( l), ρμ * ρλ, (1. - ρμ) * ρλ)
            kk += 1
          }
          k += 1
        }
      }
      info( "[ll %12.2f] [z %12.2f] [y %12.2f] [x %12.2f]\n"
        .format( llz + lly + llx, llz, lly, llx))

      for( l10 <- 0.until( L, 10)) {
        for( l <- l10 until (l10 + 10)) {
          if( l < L) info( "%3s %2d ".format( langL( l), 99 * hL( l) / N))
        }
        info( "\n")
      }

      if( iter % conf("dumpfreq",100) == 0) {
        // write to log
        val columns = Seq( 
          "%d".format( iter), 
          "%.2f".format( llx+lly+llz), 
          "%.2f".format( llx), 
          "%.2f".format( lly), 
          "%.2f".format( llz), 
          "%d".format( K), 
          "%.4f".format( α), 
          "%.4f".format( ρμ), 
          "%.4f".format( ρλ), 
          "%.4f".format( ζλ)) ++
          (0 until L).map( l => "%.4f".format( hL( l).toDouble / N))
        foLog.write( columns.mkString("\t") + "\n")
        foLog.flush

        foz.write( "" + iter + "\t" + zN.map( "%d" format _).mkString("\t") + "\n")
        foz.flush

        // convert binary to hex, LITTLE ENDIAN
        val foy = new java.io.FileWriter( fn_base + ".y." + iter + ".hex")
        for( n <- 0 until N) {
          val hex = yNL( n).map( "%d" format _).mkString.grouped( 4).map{ y4 =>
            Integer.toHexString( Integer.parseInt( y4.reverse, 2))
          }.toSeq.mkString
          foy.write( hex + "\n")
        }
        foy.close
      }
    }
  }

  foz.close
  foLog.close
  foInfo.close

  def logBeta2( a: Double, b: Double): Double = {
    lgamma( a) + lgamma( b) - lgamma( a + b)
  }


  // ordered draws
  def logPolya2D( n1: Int, n0: Int, α1: Double, α0: Double): Double = {
    logBeta2( α1 + n1, α0 + n0) - logBeta2( α1, α0)
  }

  def unif(): Double = {
    Rand.uniform.get()
  }

  def pick( a_ : Seq[ Double], n: Int): Int = {
    var i = 0
    var r = 0.
    while( i < n) {
      r += a_( i)
      i += 1
    }
    r *= unif()
    i = 0
    var s = a_(0)
    while( r > s && i < n-1) {
      i += 1
      s += a_( i)
    }
    i
  }

  def laccept( l: Double): Boolean = l >= 0 || l > -10. && log( unif()) < l
  def accept( p: Double): Boolean = unif() < p
}
