library(cummeRbund)

args = commandArgs(trailingOnly=TRUE)

setwd(args[1])

cuff = readCufflinks(args[2])

comps = strsplit(args[3], ",", fixed = TRUE)[[1]]
comps = gsub("-", "_", comps)
comps = gsub("\\.", "_", comps)
comps = gsub(":", ":X", comps)
for (i in 1:length(comps)) {comps[i] = paste("X", comps[i], sep="")}

write.table(replicates(cuff), "replicates.csv", sep=",", quote=F)
write.table(annotation(genes(cuff)), "annotations.csv", sep=",", quote=F)
write.table(fpkm(genes(cuff)), "fpkm-genes.csv", sep=",", quote=F)
write.table(repFpkm(genes(cuff)), "repFpkm-genes.csv", sep=",", quote=F)
write.table(count(genes(cuff)), "count-genes.csv", sep=",", quote=F)
write.table(fpkm(isoforms(cuff)), "repFpkm-genes.csv", sep=",", quote=F)
write.table(diffData(genes(cuff)), "diffData-genes.csv", sep=",", quote=F)


csDensity(genes(cuff))
dispersionPlot(genes(cuff))
fpkmSCVPlot(genes(cuff))
fpkmSCVPlot(isoforms(cuff))
csBoxplot(genes(cuff))
csDendro(genes(cuff))

csScatterMatrix(genes(cuff))
csVolcanoMatrix(genes(cuff))
sigMatrix(cuff, level='genes', alpha=0.05)
PCAplot(genes(cuff),"PC1", "PC2")
csDistHeat(genes(cuff))


pdf(file="scatter-pairs.pdf")
plot_list = list()
for (i in 1:length(comps)) {
    x_y = strsplit(comps[i], ":", fixed=TRUE)[[1]]
    x = x_y[1]
    y = x_y[2]
    plot_list[[i]] = csScatter(genes(cuff), x, y, smooth=T)
}
for (i in 1:length(comps)) {
    print(plot_list[[i]])
}
dev.off()

pdf(file="maplot-pairs.pdf")
plot_list = list()
for (i in 1:length(comps)) {
    x_y = strsplit(comps[i], ":", fixed=TRUE)[[1]]
    x = x_y[1]
    y = x_y[2]
    plot_list[[i]] = MAplot(genes(cuff), x, y, smooth=T)
}
for (i in 1:length(comps)) {
    print(plot_list[[i]])
}
dev.off()

pdf(file="volcano-pairs.pdf")
plot_list = list()
for (i in 1:length(comps)) {
    x_y = strsplit(comps[i], ":", fixed=TRUE)[[1]]
    x = x_y[1]
    y = x_y[2]
    plot_list[[i]] = csVolcano(genes(cuff), x, y, smooth=T)
}
for (i in 1:length(comps)) {
    print(plot_list[[i]])
}
dev.off()


gene_diff_data=diffData(genes(cuff))
sig_gene_data = subset(gene_diff_data,significant=='yes')
sig_genes = getGenes(cuff,sig_gene_data$gene_id)

write.table(sig_gene_data,'sig-diff-genes.csv',sep=',',quote=F)

pdf(file="expression-barplot.pdf")
lclsigs = getSig(cuff,alpha=0.05,level='genes')[1:10]
expressionBarplot(getGenes(cuff,lclsigs),logMode=T,showErrorbars=T)
dev.off()

pdf(file="expression-heatmap.pdf")
lclsigs = getSig(cuff,alpha=0.05,level='genes')[1:10]
csHeatmap(getGenes(cuff,lclsigs),cluster='both')
dev.off()
