library( "grid")
library( "Cairo")
CairoFonts(
regular="Gentium:style=Regular",
bold="Gentium:style=Bold",
italic="Gentium:style=Italic",
bolditalic="Gentium:style=Bold Italic,BoldItalic")

# general settings

prefix = 'poly'

# read data

TH.table = read.table( paste( prefix, '.th.tsv', sep=''), sep='\t', header=T)
TH <- as.matrix( TH.table)
langs <- colnames(TH)
L <- dim(TH)[2]

EZ.table = read.table( paste( prefix, '.ez.tsv', sep=''), sep='\t', header=T)
EZ <- as.matrix( EZ.table)
etyma <- colnames(EZ)
K <- dim(EZ)[1]
N <- dim(EZ)[2]

EZ.hist <- t(as.data.frame(lapply( as.list(data.frame(t(EZ))), function( l) { sort(l, decreasing=T)})))

K.size <- rowSums( EZ)

# color and layout

ramp.array <- rev( heat.colors( 101))
ramp <- function( X) { ramp.array[ 1 + floor( X * 100)] }
col1 <- '#bbddff'

u <- function( x) { unit( x, 'native')}
inch <- function( x) { unit( x, 'in')}
mm <- function( x) { unit( x, 'mm')}

nonpoly <- unlist( strsplit( 'NGG,LAU,SAA,MTA,NGU,ROT,FIJ,WYA', ','))
shift.right <- unlist( strsplit( 'HAW,PEN,MQA,TUA,RAR,TAH,MIA,MVA,EAS', ','))
shift.down <- unlist( strsplit( 'MAO,EAS,REN,PIL,TIK,ANU,MFA,MAE,WUV,WFU,MTA,NGU', ','))

lang.table <- do.call( rbind, lapply( c(
  'NKO,KAP,   ,   ,   ,   ,   ,   ,   ,HAW,   ',
  '   ,NGR,TAK,OJA,   ,   ,   ,   ,   ,   ,   ',
  'NGG,LAU,SAA,SIK,   ,   ,   ,ECE,TOK,PEN,MQA',
  '   ,REN,PIL,TIK,ANU,   ,ROT,EUV,PUK,   ,TUA',
  '   ,   ,MTA,MFA,NGU,MAE,FIJ,EFU,SAM,RAR,TAH',
  '   ,   ,   ,   ,WUV,WFU,WYA,TON,NIU,MIA,MVA',
  '   ,   ,   ,   ,   ,   ,   ,MAO,   ,   ,EAS'
), function(l){unlist(strsplit(l,','))}))

lang.boxes <- rbind(
  c(7, -0.2, 4.2, 1),
  c(9.2, 0, 1, 7),
  c(9.2, 0, 2, 5),

  c(7, 1, 2, 4),

  c(1, 3-0.2, 5, 1),
  c(3, 2-0.2, 1, 2),
  c(4, 1-0.2, 2, 1),
  c(5, 1-0.2, 1, 3),

  c(0, 6, 2, 1),
  c(1, 5, 1, 2),
  c(1, 5, 3, 1),
  c(3, 4, 1, 2))

plot.cluster <- function( theta, caption=NA, font.size=12, hist=NULL,
  caption.size=font.size, scale.tile=1, outline=T) 
{
  y.hist = 1
  y.map = ifelse( is.null(hist), 0, 2) + y.hist

  pushViewport(viewport(w=u(0.95), h=u(0.95), 
    xscale = c(0, 11), yscale = c(0, 7 + y.map)))

  rows <- dim(lang.table)[1]
  cols <- dim(lang.table)[2]

  # draw map
  off <- ifelse( outline, 0.1, 0) # recenter
  pushViewport(viewport(x=u(0-off), y=u(y.map+off), w=u(11), h=u(7),
    xscale = c(0,11), yscale = c(0,7), just=c(0,0)))

  # region indicators first
  if( outline) {
    for( i in 1:dim(lang.boxes)[1]) {
      x <- lang.boxes[i,1]
      y <- lang.boxes[i,2]
      w <- lang.boxes[i,3]
      h <- lang.boxes[i,4]

      grid.roundrect( u(x), u(y), width=u(w), height=u(h), just=c(0,0),
        r = mm(2), gp =gpar( fill=col1, col=col1))
    }
  }

  for( r in 1:rows) {
    for( c in 1:cols) {
      lang <- lang.table[r,c]
      if( lang %in% langs) {
        l <- which( langs == lang)
        x <- c + ifelse( outline && lang %in% shift.right, 0.2, 0)
        y <- r + ifelse( outline && lang %in% shift.down, 0.2, 0)

        grid.roundrect( u(x-0.5), u((8-y)-0.5),
          width=u(0.9 * scale.tile), height=u(0.9 * scale.tile),
          r = mm(0.9*2*scale.tile), gp = gpar( lwd=0, fill=ramp( theta[l])))

        grid.text( lang, x=u(x-0.5), y=u((8-y)-0.5),
          gp = gpar( fontsize=font.size*scale.tile, col=rgb(0,0,0,
            ifelse( lang %in% nonpoly && !outline, 0.3, 1.0))))
      }
    }
  }

  popViewport()  # end map

  # histogram of membership in cluster
  if( !is.null(hist)) {
    pushViewport(viewport(y=u(y.hist), w=u(11), h=u(1.8), just=c(0.5,0),
      xscale = c(0, 200), yscale = c(0, 1)))

    grid.rect( gp=gpar( fill=NA, col=col1, lwd=0.5))
    for( i in 1:3) {
      grid.lines( x=u(c(i*50,i*50)), y=u(c(0,1)),
        gp=gpar( col=col1, lwd=0.5))
    }

    grid.lines( x=u(1:200), y=u(hist[1:200]),
      gp=gpar( col='#000000', lwd=0.5))

    popViewport()  # end histogram
  }

  if( !is.na( caption)) {
    grid.text( caption, x=u(5.5), y=u(0.5), 
      just=c(0.5,0.5), gp = gpar( fontsize=caption.size))
  }

  popViewport()
}

# plot

K.toplot <- 12
cols <- 4
rows <- 3
labels <- unlist( strsplit( 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', ''))

CairoPDF( paste( prefix, '.plot.pdf', sep=''), family="Gentium", 
  width=9.9, height=9.9/cols/11*10*ceiling( K.toplot/cols))

pushViewport(viewport(layout = grid.layout(rows,cols)))
for( k.i in 1:K.toplot) {
  pushViewport(viewport(
    layout.pos.col = ((k.i-1) %% cols) + 1, 
    layout.pos.row = ceiling( k.i/cols),
    just = c(0,0)
    ))
  caption <- sprintf( '%s: %.1f etyma, %d at 50%%', 
    labels[k.i], 
    K.size[k.i], 
    sum(EZ[k.i,]>=0.5))
  plot.cluster( TH[k.i,], caption, 4.7, hist=EZ.hist[k.i,], caption.size=10)
  popViewport()
}
dev.off()
