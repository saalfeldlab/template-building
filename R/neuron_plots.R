
source("/groups/saalfeld/home/bogovicj/dev/template/template-building-pub/R/funs_views.R")

src_dir = "/nrs/saalfeld/john/projects/flyChemStainAtlas/all_evals/nblast/v14_neurons"
name2file <- function( name ){sprintf( '%s_xfm_filt_xfm.swc', name ) }

# The neuron names for the three sets of neurons below came from 
# Fig_5A_DM2_DA1_Mz19.R

# VM2 (first row in FAFB paper Fig5A)
vm2_names = c("51886", "54072")
vm2_file_list = sapply( vm2_names, name2file ) %>% paste( src_dir, ., sep='/')
vm2_jfr18 = read.neurons( vm2_file_list )

# DM2 (second row in FAFB paper Fig5A)
dm2_names = c("56424", "67408")
dm2_file_list = sapply( dm2_names, name2file ) %>% paste( src_dir, ., sep='/')
dm2_jfr18 = read.neurons( dm2_file_list )

# MZ19 (third row in FAFB paper Fig5A)
mz19_names = c("27295", "57311", "57323", "57353", "57381", "61221", "755022", "2863104", "32399", "57241", "57414", "36390", "42421"  )
mz19_file_list = sapply( mz19_names, name2file ) %>% paste( src_dir, ., sep='/')
mz19_jfr18 = read.neurons( mz19_file_list )


## VM2
# vm2_jfr18_fcwb = 
vm2_plot = nlapply(vm2_jfr18, subset, in_j2_rca)

#3d
open3d()
all_3d_plot(vm2_jfr18)
rgl.snapshot("JRC2018_VM2_gross.png")

#2d
pdf("JRC2018_VM2.pdf")
ca_2d_plot(vm2_plot)
dev.off()


## DM2
dm2_plot = nlapply(dm2_jfr18, subset, in_j2_rca)
#3d
open3d()
all_3d_plot(dm2_jfr18)
rgl.snapshot("JRC2018_DM2_gross.png")

#2d
pdf("JRC2018_DM2.pdf")
ca_2d_plot(dm2_plot)
dev.off()

## MZ19
mz19_plot = nlapply(mz19_jfr18, subset, in_j2_rca)
#3d
open3d()
all_3d_plot(mz19_jfr18)
rgl.snapshot("JRC2018_MZ19_gross.png")

#2d
pdf("JRC2018_MZ19.pdf")
ca_2d_plot(mz19_plot)
dev.off()
