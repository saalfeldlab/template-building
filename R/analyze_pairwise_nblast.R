library(nat.nblast)

swcFiles <- list.files( pattern = "swc$" )

p1 = c("20161102_32_C1", "20161102_32_C3")
p2 = c("20161102_32_D1", "20161102_32_D2")
p3 = c("20161102_32_E1", "20161102_32_E3")
p4 = c("20161220_31_I1", "20161220_31_I2", "20161220_31_I3")
p5 = c("20161220_32_C1", "20161220_32_C3")
p6 = c("20170223_32_A2", "20170223_32_A3", "20170223_32_A6")
p7 = c("20170223_32_E1", "20170223_32_E2", "20170223_32_E3")
p8 = c("20170301_31_B1", "20170301_31_B3", "20170301_31_B5")
p=list(p1,p2,p3,p4,p5,p6,p7,p8) 
#p=list(p1,p2) 

# unlist - "flattens" a list
# unlist( p[1:2] )

# prefix="20161102_32_C3";
# swcFiles[ startsWith( swcFiles, prefix )]


# For debugging
# swcFiles = swcFiles[c(1:4)]

N = length(swcFiles) * length(swcFiles)

i = 1
df <- NULL
for ( x in swcFiles ){ 
  
  #n1 = read.neuron( x )
  
  for ( y in swcFiles ){
    print( sprintf( "pair %d of %d", i, N ) )
    if( i > 60 && i < 65) {
      
      print( x )
      print( y )
      print( " " )
    }

    n2 = read.neuron( y )
    
    tryCatch(
      {
        scores = nblast( n1, n2 )
        norm_scores = nblast( n1, n2, normalised = TRUE )
        rbind(df,list( x, n1[["NumPoints"]], y, n2[["NumPoints"]], scores[[1]], norm_scores[[1]]))->df
      }, warning = function(war) {
        print(paste("Warning:  ",war))
      }, error = function(err) {
        print(paste("Error:  ", err))
      }, finally = {
      }
      
    )
    i = i + 1
  }
}
colnames(df)<-list("n1File","n1Size","n2File","n2Size","score","norm_score")
write.csv( df, file="nblastResults.csv" )



# for ( i in 1:length(p)){ 
#   print( i )
#   
#   # Remove the ith element from p
#   tmp  = p
#   tmp[[i]] <- NULL
#   
#   xList = unlist(tmp)
#   yList = unlist(p[[i]])
#   
#   #print( xList )
#   #print( yList )
#   
#   for ( x in xList ){ 
#     for( y in yList ){
#       print(x)
#       print(y)
#     }
#   }
#   break
# }