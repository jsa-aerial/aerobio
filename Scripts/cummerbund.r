library(cummeRbund)

args = commandArgs(trailingOnly=TRUE)

setwd(args[1])

cuff = readCufflinks(args[2])

pdf(file="density-19f-dapto-t1.pdf")
csDensity(genes(cuff))
dev.off()

pdf(file="scatter-19f-dapto-t1.pdf")
csScatter(genes(cuff), 'X19f_dapto','X19f_t1')
dev.off()

pdf(file="scatter-matrix-19f-dapto-t1.pdf")
csScatterMatrix(genes(cuff))
dev.off()

pdf(file="volcano-19f-dapto-t1.pdf")
csVolcanoMatrix(genes(cuff))
dev.off()

gene_diff_data=diffData(genes(cuff))
sig_gene_data = subset(gene_diff_data,significant=='yes')
sig_genes = getGenes(cuff,sig_gene_data$gene_id)

write.table(sig_gene_data,'sig-diff-genes.csv',sep=',',quote=F)

pdf(file="sig-gene-diff-barplot-19f-dapto-t1.pdf")
expressionBarplot(sig_genes,logMode=T,showErrorbars=T)
dev.off()

pdf(file="sig-gene-diff-heatmap-19f-dapto-t1.pdf")
csHeatmap(sig_genes,cluster='both')
dev.off()

plot_list = list()
for (i in 1:length(sig_gene_data$gene_id)) {
    ex_gene = getGene(cuff,sig_gene_data$gene_id[i])
    plot_list[[i]] = expressionBarplot(ex_gene,logMode=T,showErrorbars=T)
}

pdf(file="sig-genes-individual.pdf")
for (i in 1:length(sig_gene_data$gene_id)) {
    print(plot_list[[i]])
}
dev.off()
