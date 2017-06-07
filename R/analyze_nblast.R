library(nat.nblast)

# Neuron file
args = commandArgs( trailingOnly = FALSE )

realargs = args[2:length(args)] # First arg is the R executable
files = realargs[ !startsWith( realargs, "--" )]
files

n = read.neuron( files[1] )
nc = read.neuron( files[2] )
scores = nblast( nc, n )
norm_scores = nblast( nc, n, normalised = TRUE )
scores
norm_scores
