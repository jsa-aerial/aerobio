## RNA-seq analysis with DESeq2
## modified from example from Stephen Turner, @genetics_blog

library(DESeq2)
library(RColorBrewer)
library(gplots)


## args[1] == full path to Charts directory for this analysis
## args[2] == full path to featureCounts csv for this analysis
## args[3] == comma separated string of conditions to compare and rep count: "c1,c2,reps"
## args[4] == script directory
##
args = commandArgs(trailingOnly=TRUE)
setwd(args[1])
source(paste(paste(args[4],"/",sep=""),"rld_pca.r",sep=""))
source(paste(paste(args[4],"/",sep=""),"maplot.r",sep=""))
source(paste(paste(args[4],"/",sep=""),"volcanoplot.r",sep=""))


## preprocess phase --------------------------------------------------

## Read featureCounts matrix
countdata <- read.table(args[2], header=TRUE, row.names=1)

## Remove first five columns (chr, start, end, strand, length)
countdata <- countdata[ ,6:ncol(countdata)]

## Remove .bam or .sam from filenames
colnames(countdata) <- gsub("\\.[sb]am$", "", colnames(countdata))

## Convert to matrix
countdata <- as.matrix(countdata)
head(countdata)

## Assign condition
c1c2reps <- strsplit(args[3], ",", fixed = TRUE)[[1]]
c1 <- c1c2reps[0]
c2 <- c1c2reps[1]
reps <- c1c2reps[2]
(condition <- factor(c(rep(c1, reps), rep(c2, reps))))


## Analysis phase --------------------------------------------------


## Create a coldata frame and instantiate the DESeqDataSet. See ?DESeqDataSetFromMatrix
##
(coldata <- data.frame(row.names=colnames(countdata), condition))
dds <- DESeqDataSetFromMatrix(countData=countdata, colData=coldata, design=~condition)
dds

## Run the DESeq pipeline
dds <- DESeq(dds)


# Plot dispersions
png("qc-dispersions.png", 1000, 1000, pointsize=20)
plotDispEsts(dds, main="Dispersion plot")
dev.off()

## Regularized log transformation for clustering/heatmaps, etc
rld <- rlogTransformation(dds)
head(assay(rld))
hist(assay(rld))


## Colors for plots below via RColorBrewer
(mycols <- brewer.pal(8, "Dark2")[1:length(unique(condition))])

## Create heatmap of sample distance
sampleDists <- as.matrix(dist(t(assay(rld))))
png("qc-heatmap-samples.png", w=1000, h=1000, pointsize=20)
heatmap.2(as.matrix(sampleDists), key=F, trace="none",
          col=colorpanel(100, "black", "white"),
          ColSideColors=mycols[condition], RowSideColors=mycols[condition],
          margin=c(10, 10), main="Sample Distance Matrix")
dev.off()


## Do some PCA
## Could do with built-in DESeq2 function:
## DESeq2::plotPCA(rld, intgroup="condition")
png("qc-pca.png", 1000, 1000, pointsize=20)
rld_pca(rld, colors=mycols, intgroup="condition", xlim=c(-75, 35))
dev.off()


## Get differential expression results
res <- results(dds)
table(res$padj<0.05)
## Order by adjusted p-value
res <- res[order(res$padj), ]
## Merge with normalized count data
resdata <- merge(as.data.frame(res), as.data.frame(counts(dds, normalized=TRUE)), by="row.names", sort=FALSE)
names(resdata)[1] <- "Gene"
head(resdata)
## Write results
write.csv(resdata, file="diffexpr-results.csv")


## MA plot
## Could do with built-in DESeq2 function:
## DESeq2::plotMA(dds, ylim=c(-1,1), cex=1)
png("diffexpr-maplot.png", 1500, 1000, pointsize=20)
maplot(resdata, main="MA Plot")
dev.off()


## Volcano plot...
png("diffexpr-volcanoplot.png", 1200, 1000, pointsize=20)
volcanoplot(resdata, lfcthresh=1, sigthresh=0.05, textcx=.8, xlim=c(-2.3, 2))
dev.off()
