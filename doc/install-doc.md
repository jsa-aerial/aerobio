
# TVO Lab : Aerobio Install Guide and Manual analysis

<a href="https://jsa-aerial.github.io/aerobio/index.html"><img src="https://github.com/jsa-aerial/aerobio/blob/master/resources/public/images/tvolabicon.png" align="left" hspace="10" vspace="6" alt="aerobio logo" width="150px"></a>

This is a guide to the requirments needed to run [Aerobio](https://github.com/jsa-aerial/aerobio/blob/master/doc/user-guide.md) automated HTS analyses. It covers the tools, packages, and scripts needed, their setup, and how to run them manually in order to verify they are properly available. The manual running sections can also be used on their own to analyze small data sets if desired. Lastly, there are instructions for obtaining and using the Aerobio self installing uberjar to install and run Aerobio plus an overview of how you can configure it.

Table of Contents
=================

* [Tools](#tools)
   * [Java](#java)
   * [BCL converters (convert BCL files from sequencers to fastqs)](#bcl-converters-convert-bcl-files-from-sequencers-to-fastqs)
   * [Demultiplexers](#demultiplexers)
   * [Aligners (generate sam/bam output from 'raw' fastq files)](#aligners-generate-sambam-output-from-raw-fastq-files)
   * [Scripting languages](#scripting-languages)
   * [Read analyzers](#read-analyzers)
   * [Fastq processors](#fastq-processors)
   * [breseq](#breseq)
   * [Miscellaneous Python packages:](#miscellaneous-python-packages)
* [RNA-Seq, Tn-Seq and WG-Seq By Hand](#rna-seq-tn-seq-and-wg-seq-by-hand)
* [Scripts](#scripts)
   * [RNA-Seq:](#rna-seq)
   * [Fitness:](#fitness)
   * [Aggregation:](#aggregation)
* [Flows](#flows)
   * [Starting from sequencer output](#starting-from-sequencer-output)
   * [Demultiplexing the fastq files](#demultiplexing-the-fastq-files)
   * [Alignment (BAM and MAP file generation, BAM sorting, BAM indexing)](#alignment-bam-and-map-file-generation-bam-sorting-bam-indexing)
      * [Index generation](#index-generation)
      * [Alignment](#alignment)
   * [RNA-Seq DGE](#rna-seq-dge)
   * [Tn-Seq Fitness](#tn-seq-fitness)
   * [WG-Seq breseq](#wg-seq-breseq)
   * [dualRNA-Seq](#dualrna-seq)
   * [TRADis-Seq](#tradis-seq)
* [Aerobio](#aerobio)
   * [Uberjar](#uberjar)
   * [Aerobio command](#aerobio-command)
   * [Aerobio server](#aerobio-server)
   * [Config file](#config-file)

<!-- Created by https://github.com/ekalinin/github-markdown-toc -->

# Tools

Tools are command line processors, libraries, packages, and scripts in Python, R, and Perl. Generally these should be installed before attempting to run any analysis.  However, not all are required for every scenario.  For example, if you are starting with a set of fastq files, you won't need to run any BCL converter.  If you are only running RNA-Seq, you won't need bowtie1 or Python.  Alternately, if you are only running Tn-Seq, you won't need R and DESeq2, but will require bowtie1 and Python. If you won't be doing any work on eukaryotes, you won't need STAR. Trying to figure out all the various combinations may be more difficult and time consumming than simply installing everything.

Many of these tools now provide statically linked executables and thus do not require building from source.  However some still require building from source.  How to do that will vary depending on the OS you are using and should be detailed in the installation instructions for the tool.


## Java
  Needed for running Aerobio services as well as tools such as IGV.  It is also required for a host of other tools and applications and so may already be installed.  If not installed, you should install a Long Term Service (LTS) version.  Versions tested and known to work with Aerobio itself include Java LTS versions 8, 17, 21 and 28 (the latest as of this writing).
  - https://www.oracle.com/java/technologies/downloads/

## BCL converters (convert BCL files from sequencers to fastqs)
  - bases2fastq (if using ElementBio sequencers)
  https://support.illumina.com/sequencing/sequencing_software/bcl2fastq-conversion-software/downloads.html

  - bcl-convert (if using Illumina sequencers)
  https://emea.support.illumina.com/sequencing/sequencing_software/bcl-convert/downloads.html

## Demultiplexers
  Needed to demultiplex subreads of sequencer samples (fastqs from BCL converters)
  - seqkit Recommended as it is simple statically linked stand alone executable
  https://bioinf.shenwei.me/seqkit
  - demultiplex Requires version specific Python
  https://demultiplex.readthedocs.io/en/latest/installation.html

## Aligners (generate sam/bam output from 'raw' fastq files)
  - STAR
  https://github.com/alexdobin/STAR

  - bowtie1
  https://sourceforge.net/projects/bowtie-bio/files/bowtie/1.3.1/

  - bowtie2
  https://sourceforge.net/projects/bowtie-bio/files/bowtie2/2.5.4/


## Scripting languages
  - Python:
  This typically will already be installed on your system by default.  If not, see https://www.python.org/downloads/.  You will also need the `pip` package installer.  This is often installed when Python is installed, but it may need to be installed separately.  See https://pip.pypa.io/en/stable/installation/

  - R
  https://www.r-project.org/

  - Perl:
  If your OS is Linux (Ubuntu, etc), this should be already installed by default. Otherwise https://www.perl.org/get.html


## Read analyzers
  - subthread (featureCounts, et al)
  https://sourceforge.net/projects/subread/files/subread-2.1.1/

  - DESeq2 (R;  Differential gene analysis)
  https://bioconductor.org/packages/release/bioc/html/DESeq2.html

  - Fitness and aggregation (Python and Perl)
  https://bioperl.org/howtos/SeqIO_HOWTO.html


## Fastq processors
  - samtools (sam to bam converters, sorters, indexers, etc)
  https://www.htslib.org/download/

  - cutadapt
  https://cutadapt.readthedocs.io/en/stable/installation.html

  - UMI tools
  https://umi-tools.readthedocs.io/en/latest/INSTALL.html


## breseq
  - download: https://github.com/barricklab/breseq/releases
  - manual : https://gensoft.pasteur.fr/docs/breseq/0.35.0/


## Miscellaneous Python packages:
  For all of these `pip install <package>` should work.
  - biopython https://biopython.org/docs/1.75/api/Bio.html
  - msgpack https://github.com/msgpack/msgpack-python
  - trio https://trio.readthedocs.io/en/stable/
  - trio-websocket https://pypi.org/project/lager-trio-websocket/0.9.0.dev0/




# RNA-Seq, Tn-Seq and WG-Seq By Hand

The following sections, up to the [Aerobio](#aerobio) section, are a guide for running basic RNA-Seq and Tn-Seq by hand (manual runs). Included are some notes on slight variations of these.  Additionally, it includes a short review of how the TVO Lab uses [breseq](https://gensoft.pasteur.fr/docs/breseq/0.35.0/) for whole genome seq analysis. The primary intent for this is to provide you with verification that the tools, packages, libraries, etc, needed to run the analyses, are available in your environment and properly installed and configured. However you can also use the instructions to manually process (typically smaller) sequencer runs.

The [tool](#tools) section gives the tools needed and download links for installing them.  The [scripts](#scripts) section gives the links to the scripts for running DGE analysis on RNA-Seq data and fitness + aggregation on Tn-Seq data.  Then the [flows](#flows) section gives step by step procedures for generating the input data to the main scripts and then running the scripts to obtain the final output.




# Scripts

These scripts operate on different outputs from the above toolset.

## RNA-Seq:
Requires the count matrices output from featureCounts, R, and the R package DESeq2.  Computes Differential Gene Output matrices, plus various PCA, Map, and Volcano plots.
  - https://github.com/jsa-aerial/aerobio/blob/master/Scripts/deseq2-rnaseq.r
  - https://github.com/jsa-aerial/aerobio/blob/master/Scripts/rld_pca.r
  - https://github.com/jsa-aerial/aerobio/blob/master/Scripts/maplot.r
  - https://github.com/jsa-aerial/aerobio/blob/master/Scripts/volcanoplot.r


## Fitness:
Requres the MAP file output from bowtie1, the reference genome(s) GTF/GFF file and Python plus `biopython` (see `Miscellaneous Python packages` under [tools](#tools)) . Computes the fitness matrices that will be input to the aggregation step.
  - https://github.com/jsa-aerial/aerobio/blob/master/Support/py/collapse.py
  - https://github.com/jsa-aerial/aerobio/blob/master/Support/py/newmap2oldmap.py
  - https://github.com/jsa-aerial/aerobio/tree/master/Support/py/multicalc

For multicalc fitness script, the best way to run this is to first build an executable zip file composed of the elements in the `multicalc` directory. The steps are:

    1. cd to the multicalc directory
    2. issue: `zip -r ../calc_fitness.zip *`
    3. issue `cd ..`
    4. issue `echo "#\!/usr/bin/env python3" | cat - calc_fitness.zip > calc_fitness`
    5. issue `chmod a+x calc_fitness`


## Aggregation:
Requires the fitness output csv files, the reference GBK file, the normalization gene file and Perl. Computes weighted averages of gene fitness results generating gene summary output csv.
  - https://github.com/jsa-aerial/aerobio/blob/master/Scripts/get_norm_genes.py
  - https://github.com/jsa-aerial/aerobio/blob/master/Scripts/aggregate.pl




# Flows

All commands and filesystem actions shown in the following assume a Unix (typically Linux) environment.  All tools are presumed to have been installed and their executables are on the command path. The command line prefix is just `$`. All command interaction shown assumes a command line terminal session.  Additionally, the userid issuing the commands is presumed to have all necessary permissions, in particular, permissions to read and write to the directories and files shown.


## Starting from sequencer output

Whether you are working with RNA-Seq or Tn-Seq, ff you are starting with the direct sequencer output of either Illumina or Element Biosciences (ElemBio), you first need to convert that to sample fastq files. For Illumina, you will need bcl-convert and for ElemBio you will need bases2fastq (both listed under **Tools**). It is usually best to change directories to the sequencer's output directory, which we take here as SeqOutput

* Illumina bcl-convert

```sh
$ cd /.../SeqOutput
$ bcl-convert --no-lane-splitting true --bcl-input-directory ./ --output-directory  ./Data/Fastq -f
```
The no lane splitting will keep all lanes of a flow cell in the same fastq file. This keeps later use simpler.  The `-f` option supports using the output directory `Data/Fastq` even if it exists already.

The fastq files generated will be placed in the output directory with names reflecting the definitions provided in the SampleSheet.csv (should be in the `SeqOutput` directory as part of the sequencer output).  Thread control in bcl-convert is largely automated with fairly complex switches for those who really understand the issues with correct parallelization.


* ElemBio bases2fastq

```sh
$ cd /.../SeqOutput
$ bases2fastq ./ ./Data --no-projects --group-fastq
```

No lane splitting is the default for `bases2fastq`.  The `--no-projects` together with `--group-fastq` will place all the output sample fastq files into `Data/Samples` with names reflecting the definitions provided in the RunParameters.csv (which should be in the `SeqOutput` directory as part of the sequencer output). If you have a machine with large resources you may also want to use the `--num-threads` switch with a number reflecting a portion of the CPU cores available to use.


## Demultiplexing the fastq files

If you are starting with fastq files provided to you or have finished the conversion process to generate them yourself, the next step is to demultiplex the experiment reads. These are the subsets of reads within a sequencer output sample fastq which have their own internal barcode.  if there are no such reads, and thus the sequencer output samples need no demultiplexing, you would go directly do alignment.

Here we use the `seqkit` suite of tools and specifically the `grep` subcommand.  There are many options. We give two basic forms: one for barcodes expected to have 'degenerate' bases such as `R`, `Y`, `N` et.al.  The second assumes the bases should be exact.

- Assumes you are in `SeqOut`, the converted fastq files are in `./Data/Samples`
```sh
## The -d option indicates potential degenerate bases and that the search
## by seq -s flag is autmatically on

$ seqkit grep -p 'barcode bases pattern' -d ./Data/Sample/<fastq file with subreads> -o output.fastq.gz
```

- Assumes you are in `SeqOut`, the converted fastq files are in `./Data/Samples` and that the demultiplexed samples will be written to `Data/Demux`. Further assumes that the current internal barcoded reads to be pulled are in BC01_R1.fastq.gz and that they have barcode `ACTGAC` with the expectation there are no degnerate bases.
```sh
## The -r indicates standard regular expression pattern and the -s indicates
## the search is done on the seq of the fastq records

$ seqkit grep -p '^ACTGAC' -r -s ./Data/Samples/BC01_R1.fastq.gz -o ./Data/Demux/ACTGAC-output.fastq.gz
```


## Alignment (BAM and MAP file generation, BAM sorting, BAM indexing)

The two main aligners used are bowtie (aka bowtie1) and bowtie2. If your work involves eukaryotes you will also likely be using STAR.

### Index generation

For each aligner you first need to create an *index* for the reference being used. For all of these, this requires a fasta file for the reference genome.  Here we will use NC_003028 (TIGR-4).

In all that follows we assume the fasta files are located where indicated and that the index *directories* exist and are writable.


* `bowtie`

```sh
$ bowtie-build /Fastas/NC_003028.fna /Refs/BT1Index/NC_003028
```

The index will be written to the directory `/Refs/BT1Index` as a set of binary files with names starting with the given name `NC_003028`. For small genomes, such as those for bacteria, this will run in a couple minutes or less on any contemporary machine.


* `bowtie2`

```sh
$ bowtie2-build /Fastas/NC_003028.fna /Refs/BT2Index/NC_003028
```

Here the index will be written to the directory `/Refs/BT2Index` as a set of binary files with names starting with the given name `NC_003028`. As with bowtie, for small genomes, such as those for bacteria, this will run in a couple minutes or less on any contemporary machine.


* `STAR`

```sh
$ STAR --runMode genomeGenerate --genomeDir /Refs/STARIndex/NC_003028/ --genomeFastaFiles /Fastas/NC_003028.fna
```

For `STAR` the output name `NC_003028` denotes a *directory* where the various files defining the index will be written.  If you run against smaller genomes (like NC_003028), this will run very fast as is.  However, if you are building an index for a eukaryote, like mouse, and your machine has large resources, you will likely want to add the switch `--runThreadN` which will run the build in parallele across the number of threads given.

```sh
$ STAR --runMode genomeGenerate --runThreadN 24 --genomeDir /Refs/STARIndex/GRCm38_mm10/ --genomeFastaFiles /GenomeSeqs/Mouse/MouseRefSeqAllChrs.fna
```


### Alignment

* RNA-Seq

For RNA-Seq getting your alignment is a straigtforward bowtie2 to BAM file process.  The starting input are the demultiplexed fastq files (see **Demultiplexing** above).  The steps are

1. Run `bowtie2` on the fastqs
2. Take the SAM output and run `samtools -b` on it to convert to BAM

Here we use the `bowtie2` index we created above and stream `bowtie2`'s SAM output to `samtools` to perform both steps in a single command.

```sh
$ bowtie2 -p 16 --very-sensitive -x /Refs/BT2Index/NC_003028 T4-L77SCEF1hr-a-ATACATT-R1.fastq.gz | samtools view -b > Bams/T4-L77SCEF1hr-a.bam
1033798 reads; of these:
  1033798 (100.00%) were unpaired; of these:
    15034 (1.45%) aligned 0 times
    985904 (95.37%) aligned exactly 1 time
    32860 (3.18%) aligned >1 times
98.55% overall alignment rate
$
```

The resulting BAMs are now ready for RNA-Seq processing (see below).


If you want or need coordinate sorted BAMs (as required for tools such as IGV), you can also perform sort and indexing steps, but this is *not required* for RNA-Seq DGE.

1. Run `bowtie2` on the fastqs
2. Take the SAM output and run `samtools -b` on it to convert to BAM
3. Take the BAMs and run `samtools sort` on them to get corrdiinate sorted BAMs
4. Take the sorted BAMs and run `samtools index` on them to get the indices

Again, you can streamline this a bit by piping the output of some steps to the following step. Here we pipe the starting SAM output from `bowtie2` through `samtool`s to get the corresponding BAM and then sort it via `samtools` for final output.  The sorted BAM is then indexed by running `samtools indexing` on it.

```sh
$ bowtie2 -p 16 --very-sensitive -x /Refs/BT2Index/NC_003028 T4-L77SCEF1hr-a-ATACATT-R1.fastq.gz | samtools view -b | samtools sort --threads 8 > Bams/T4-L77SCEF1hr-a.bam
1033798 reads; of these:
  1033798 (100.00%) were unpaired; of these:
    15034 (1.45%) aligned 0 times
    985904 (95.37%) aligned exactly 1 time
    32860 (3.18%) aligned >1 times
$
$ samtools index Bams/T4-L77SCEF1hr-a.bam Bams/T4-L77SCEF1hr-a.bam.bai
```

* Tn-Seq

For Tn-Seq, again the starting input are the demultiplexed fastq files, but we need to perform a couple extra pieces of processing to get the alignment output that is acceptable for Tn-Seq fitness processing.

We need to use `bowtie`(1) as we will need the older style *MAP* file output.  But due to the input format requirement of the `fitness` script we need to run a preprocessor on the fastq files before input to bowtie and then after alignment we will need to run a post processor to obtain the final result.  The steps then are:

1. Run the `collapser.py` script on the fastq files generating collapsed fasta files. The fastq reads that have the same sequence are collapsed to a single read with the count encoded in the fasta header.
2. Run `bowtie` on the collapsed fasta files, requesting *MAP* format output
3. Run the `newmap2oldmap.py` script on the `MAP` files from bowtie to obtain an older MAP format for input to `fitness`


```sh
$ python3 collapse.py -fqzin TIGR4-T1-1-ATGGCC-R1.fastq.gz -faot TIGR4-T1-1-ATGGCC-R1.collapse.fna
$ bowtie -f -m 1 -n 1 --best -y -p 8 -x /Refs/BT1Index/NC_003028 TIGR4-T1-1-ATGGCC-R1.collapse.fna > TIGR4-T1-1.newmap
 reads processed: 129343
# reads with at least one alignment: 124823 (96.51%)
# reads that failed to align: 4520 (3.49%)
# reads with alignments suppressed due to -m: 20131 (15.56%)
Reported 104692 alignments
$ python3 newmap2oldmap.py -mapin TIGR4-T1-1.newmap -mapot TIGR4-T1-1.map
#
# Also get a T2 point example for comparison in fitness example
#
$ python3 collapse.py -fqzin TIGR4-bioT4-1-GGATTA-R1.fastq.gz -faot TIGR4-bioT4-1-GGATTA-R1.collapse.fna
$ bowtie -f -m 1 -n 1 --best -y -p 8 -x /Refs/BT1Index/NC_003028 TIGR4-bioT4-1-GGATTA-R1.collapse.fna > TIGR4-bioT4-1.newmap
# reads processed: 30463
# reads with at least one alignment: 28294 (92.88%)
# reads that failed to align: 2169 (7.12%)
# reads with alignments suppressed due to -m: 4726 (15.51%)
Reported 23568 alignments
$ python3 newmap2oldmap.py -mapin TIGR4-bioT4-1.newmap  -mapot TIGR4-bioT4-1.map
```




## RNA-Seq DGE

For RNA differential gene expression (DGE), the two key computational parts are:

1. Count reads aligned to genomic features (typically the genome's CDS regions) getting a count matrix of this
2. Run a statistical model (typically a zero-inflated negative binomial) on this count data to determine which features (genes) are up or down regulated.

Here we use `featureCounts` to take read alignments in BAMs with the `GTF` for the reference genome to generate the count matrices.  And we use the R package `DESeq2` to generate the model and then the DGE matrices from this.  Additionally, the script that drives the DESeq2 processing, generates various overview plots.

Steps:

1. Get the featureCounts output matrix for the conditions you want to compare by running featureCounts on the set of BAMs for the conditions
2. Run the `deseq2-rnaseq.r` script on the output matrix CSV file


```sh
# Get featureCounts output matrix in CSV for our conditions
#
$ featureCounts -a /Refs/NC_003028.gtf -o Fcnts/T4-L77SCEF1hr-T4-wtCEF1hr.csv -t CDS \
>               Bams/T4-L77SCEF1hr-a.bam \
>               Bams/T4-L77SCEF1hr-b.bam \
>               Bams/T4-L77SCEF1hr-c.bam \
>               Bams/T4-wtCEF1hr-a.bam \
>               Bams/T4-wtCEF1hr-b.bam \
>               Bams/T4-wtCEF1hr-c.bam

        ==========     _____ _    _ ____  _____  ______          _____
        =====         / ____| |  | |  _ \|  __ \|  ____|   /\   |  __ \
          =====      | (___ | |  | | |_) | |__) | |__     /  \  | |  | |
            ====      \___ \| |  | |  _ <|  _  /|  __|   / /\ \ | |  | |
              ====    ____) | |__| | |_) | | \ \| |____ / ____ \| |__| |
        ==========   |_____/ \____/|____/|_|  \_\______/_/    \_\_____/
          v2.0.6

//========================== featureCounts setting ===========================\\
||                                                                            ||
||             Input files : 6 BAM files                                      ||
||                                                                            ||
||                           T4-L77SCEF1hr-a.bam                              ||
||                           T4-L77SCEF1hr-b.bam                              ||
||                           T4-L77SCEF1hr-c.bam                              ||
||                           T4-wtCEF1hr-a.bam                                ||
||                           T4-wtCEF1hr-b.bam                                ||
||                           T4-wtCEF1hr-c.bam                                ||
||                                                                            ||
||             Output file : T4-L77SCEF1hr-T4-wtCEF1hr.csv                    ||
||                 Summary : T4-L77SCEF1hr-T4-wtCEF1hr.csv.summary            ||
||              Paired-end : no                                               ||
||        Count read pairs : no                                               ||
||              Annotation : NC_003028.gtf (GTF)                              ||
||      Dir for temp files : Fcnts                                            ||
||                                                                            ||
||                 Threads : 1                                                ||
||                   Level : meta-feature level                               ||
||      Multimapping reads : not counted                                      ||
|| Multi-overlapping reads : not counted                                      ||
||   Min overlapping bases : 1                                                ||
||                                                                            ||
\\============================================================================//

//================================= Running ==================================\\
||                                                                            ||
|| Load annotation file NC_003028.gtf ...                                     ||
||    Features : 2191                                                         ||
||    Meta-features : 2191                                                    ||
||    Chromosomes/contigs : 1                                                 ||
||                                                                            ||
|| Process BAM file T4-L77SCEF1hr-a.bam...                                    ||
||    Single-end reads are included.                                          ||
||    Total alignments : 1033798                                              ||
||    Successfully assigned alignments : 793779 (76.8%)                       ||
||    Running time : 0.02 minutes                                             ||
||                                                                            ||
|| Process BAM file T4-L77SCEF1hr-b.bam...                                    ||
||    Single-end reads are included.                                          ||
||    Total alignments : 626269                                               ||
||    Successfully assigned alignments : 482447 (77.0%)                       ||
||    Running time : 0.01 minutes                                             ||
||                                                                            ||
|| Process BAM file T4-L77SCEF1hr-c.bam...                                    ||
||    Single-end reads are included.                                          ||
||    Total alignments : 1931344                                              ||
||    Successfully assigned alignments : 1479028 (76.6%)                      ||
||    Running time : 0.03 minutes                                             ||
||                                                                            ||
|| Process BAM file T4-wtCEF1hr-a.bam...                                      ||
||    Single-end reads are included.                                          ||
||    Total alignments : 899263                                               ||
||    Successfully assigned alignments : 680390 (75.7%)                       ||
||    Running time : 0.01 minutes                                             ||
||                                                                            ||
|| Process BAM file T4-wtCEF1hr-b.bam...                                      ||
||    Single-end reads are included.                                          ||
||    Total alignments : 1073705                                              ||
||    Successfully assigned alignments : 800636 (74.6%)                       ||
||    Running time : 0.02 minutes                                             ||
||                                                                            ||
|| Process BAM file T4-wtCEF1hr-c.bam...                                      ||
||    Single-end reads are included.                                          ||
||    Total alignments : 1949023                                              ||
||    Successfully assigned alignments : 1434005 (73.6%)                      ||
||    Running time : 0.03 minutes                                             ||
||                                                                            ||
|| Write the final count table.                                               ||
|| Write the read assignment summary.                                         ||
||                                                                            ||
|| Summary of counting results can be found in file "Fcnts/T4-L77SCEF1hr-T4-  ||
|| wtCEF1hr.csv.summary"                                                      ||
||                                                                            ||
\\============================================================================//
```

Once you have your featureCounts output, you can then run the R script `deseq2-rnaseq.r` to obtain the DGE results.  Here, we assume the R scripts noted above under **Scripts** are all placed in the directory `./Rscripts`.  The `deseq2-rnaseq.r` is the main (driver) script and it loads and uses the others.  This script takes four arguments:

1. The path to the DGE output directory
2. The **full** path to the featureCounts output CSV file
3. A comma separated string denoting the two conditions to compare and the count of replicates for each: "c1,c2,c1cnt,c2cnt". The contrast order for comparison is taken as c1 (first condition in string) is the "treated" and c2 is the "untreated".  Specifically, the fold change computed is `log2(c1/c2)`
4. The **full** path to the `Rscripts` directory

One other thing to note about this: per the DESeq2 author, we do not normalize the count matrix data before applying DESeq2 modeling as it uses its own normalization specifically tailored to the negative binomial used.  You shoulld not either!

You can streamline the full path arguments a bit by using the "$(realpath partial-path)" bash shortcut as shown in the following command call.


```sh
$ Rscript --no-save ./Rscripts/deseq2-rnaseq.r ./DGE \
>         "$(realpath Fcnts/T4-L77SCEF1hr-T4-wtCEF1hr.csv)" \
>         "T4-L77SCEF1hr,T4-wtCEF1hr,3,3" \
>         "$(realpath Rscripts)"
#
# lots of progress output: you can suppress this by adding `>> dge.log 2>&1`
# to the end of the above command and the output will be saved
# in `dge.log` (this assumes you are using bash command line)
#
$ ls -l DGE
total 1176
-rw-rw-r-- 1 anthonyj aerobio 389427 Dec 10 17:18 T4-L77SCEF1hr-T4-wtCEF1hr-DGE-maplot.png
-rw-rw-r-- 1 anthonyj aerobio 482913 Dec 10 17:18 T4-L77SCEF1hr-T4-wtCEF1hr-DGE-results.csv
-rw-rw-r-- 1 anthonyj aerobio 138410 Dec 10 17:18 T4-L77SCEF1hr-T4-wtCEF1hr-DGE-volcanoplot.png
-rw-rw-r-- 1 anthonyj aerobio  12716 Dec 10 17:18 T4-L77SCEF1hr-T4-wtCEF1hr-histogram-assay.png
-rw-rw-r-- 1 anthonyj aerobio  89178 Dec 10 17:18 T4-L77SCEF1hr-T4-wtCEF1hr-qc-dispersions.png
-rw-rw-r-- 1 anthonyj aerobio  34710 Dec 10 17:18 T4-L77SCEF1hr-T4-wtCEF1hr-qc-heatmap-samples.png
-rw-rw-r-- 1 anthonyj aerobio  42241 Dec 10 17:18 T4-L77SCEF1hr-T4-wtCEF1hr-qc-pca.png
$
$ head ./DGE/T4-L77SCEF1hr-T4-wtCEF1hr-DGE-results.csv
"","Gene","baseMean","log2FoldChange","lfcSE","stat","pvalue","padj","Bams.T4.L77SCEF1hr.a","Bams.T4.L77SCEF1hr.b","Bams.T4.L77SCEF1hr.c","Bams.T4.wtCEF1hr.a","Bams.T4.wtCEF1hr.b","Bams.T4.wtCEF1hr.c"
"1","SP_0742",1164.95997213729,-2.85204633276642,0.120096311580555,-23.7479927171069,1.15248186727801e-124,2.39716228393827e-121,258.438767049034,271.528901916389,314.164722802689,2002.26809612848,1942.05787226324,2201.30147266394
"2","SP_0519",917.371358347127,-2.61842014412407,0.114607037748068,-22.8469402540528,1.56742002333354e-115,1.63011682426688e-112,264.13125090474,234.076639583094,266.674706565073,1615.86548108614,1561.32323598086,1562.15683596285
"3","SP_0517",3975.23218084916,-2.51855296556933,0.110606954710315,-22.7702947989622,9.03364030013719e-115,6.26332394142845e-112,1137.35827436998,1297.72088984867,1117.23307430801,6651.21241127736,7572.1583384879,6075.71009680302
"4","SP_0626",1010.65854728636,2.47836435216131,0.121548978623021,20.3898410355866,2.05813588789626e-92,1.07023066170605e-89,1653.09731169691,1565.50456553173,1921.51911853737,285.865257523485,329.900856506255,308.064173922407
"5","SP_0515",579.098269215542,-2.7708405388982,0.139755632077846,-19.8263246904768,1.76473012766608e-87,7.34127733109089e-85,125.234644825523,176.025632966487,147.949665971034,984.781586299125,1080.99588830037,959.602196930715
"6","SP_2107",2721.99873645206,-1.70227688619704,0.0907221324897768,-18.7636339609726,1.4979913887322e-78,5.19303681427163e-76,1273.97788690691,1288.35782426535,1277.35966751946,4278.28851513962,4022.09314677594,4191.91537810508
"7","SP_1715",941.146320950658,-2.05003833567015,0.115686389339276,-17.7206527697737,2.90521543911871e-70,8.63264016195274e-68,348.380011969183,383.885688916274,367.134356298491,1566.20244905876,1599.70792683222,1381.56749262903
"8","SP_0516",408.007614348262,-2.71924341456696,0.153812816615506,-17.678913073703,6.0960102118437e-70,1.58496265507936e-67,104.741702944983,112.356786999885,106.548113353625,671.05657910174,807.115931955554,646.226571733784
"9","SP_0464",868.701189914779,2.16748153667374,0.133329094628428,16.2566283279298,2.00470589512058e-59,4.63309806872313e-57,1620.08090533381,1271.50430621537,1368.07777546055,367.021919617017,282.179348961325,303.342883900607
```


## Tn-Seq Fitness

First, typically, you will want to normalize results relative to a set of genes that are expected to have a fitness of 1. For Tn-Seq  these are genes that have product annotations of "Transposase" or "Mobile element".

So you first need to calculate the set of these normalization genes.  Assumes you are in the directory `/Refs/NormGenes` which is taken as a canonical location for these sets for various genomes.

```sh
$ python3 get_norm_genes.py -i /Refs/NC_003028.gbk
There are 2175 genes total
There are  50  normalization genes
$ head NC_003028.txt
SP_0015
SP_0016
SP_0299
SP_0300
SP_0328
SP_0343
SP_0344
SP_0345
SP_0460
SP_0495
```

We can now compute the fitness matrices (as CSV files) using the `calc_fitness` executable that was created above under **Scripts**. There are many switches and these can all be seen by running `calc_fitness` with no arguments.  Assume here you just have `calc_fitness` in your current (working) directory.  In general it is more useful to put it in a location that is already on your `PATH` as it is then accessible from anywhere.

```sh
./calc_fitness
You are missing one or more required flags. A complete list of flags accepted by calc_fitness is as follows:


Required

-ref            The name of the reference genome file, in GTF/GFF format.

-features       The feature types to use, defaults to 'CDS', can be comma separted string: 'gene,CDS' etc.
-t1             The name of the bowtie mapfile from time 1.

-t2             The name of the bowtie mapfile from time 2.

-out            Name of a file to enter the .csv output.



Optional

-expansion      Expansion factor (default: 250)

-d              All reads being analyzed are downstream of the transposon

-reads1         The number of reads to be used to calculate the correction factor for time 0.
                (default counted from bowtie output)

-reads2         The number of reads to be used to calculate the correction factor for time 6.
                (default counted from bowtie output)

-cutoff         Discard any positions where the average of counted transcripts at time 0 and time 1 is below this number (default 0)

-cutoff2        Discard any positions within the normalization genes where the average of counted transcripts at time 0 and time 1 is below this number (default 0)

-strand         Use only the specified strand (+ or -) when counting transcripts (default: both)

-reversed       Experiment protocol used reversed i5 and i7 indices

-normalize      A file that contains a list of genes that should have a fitness of 1
-out2           Name of file to hold any normalization stats.

-maxweight      The maximum weight a transposon gene can have in normalization calculations

-multiply       Multiply all fitness scores by a certain value (e.g., the fitness of a knockout). You should normalize the data.

-ef             Exclude insertions that occur in the first N amount (%) of gene--becuase may not affect gene function.

-el             Exclude insertions in the last N amount (%) of the gene--considering truncation may not affect gene function.

-wig            Create a wiggle file for viewing in a genome browser. Provide a filename.

-uncol          Use if reads were uncollapsed when mapped.
```

Here, in running the example using the MAP file generated in the Tn-Seq **Alignment** section, we use a typical (standard) set of the switches and their values:

```sh
$ ./calc_fitness -ef .0 -el .10 -cutoff 0 \
>                -expansion 250 \
>                -normalize /Refs/NormGenes/NC_003028.txt \
>                -ref /Refs/NC_003028.gtf \
>                -t1 TIGR4-T1-1.map \
>                -t2 TIGR4-bioT4-1.map \
>                -out Fitness/TIGR4-T1-TIGR4-bioT4-1.csv \
>                -out2 Fitness/TIGR4-T1-TIGR4-bioT4-1-norm-info.txt
#
# various progress output
#
$ head Fitness/TIGR4-T1-TIGR4-bioT4-1.csv
position,strand,count_1,count_2,ratio,mt_freq_t1,mt_freq_t2,pop_freq_t1,pop_freq_t2,gene,D,W,nW
23,+/+,5.87924232837219,15.502600549910879,2.6368364636201593,4.264815581620041e-06,1.1245621236231143e-05,0.9999957351844184,0.9999887543787638,,250,1.175603542345771,1.1714924415997132
35,+/,1.6034297259196881,0,0,1.163131522260011e-06,0.0,0.9999988368684778,1.0,,250,0,0.0
47,b/b,423.8399242181043,100.76690357442071,0.23774754999853895,0.000307454432384063,7.309653803550243e-05,0.9996925455676159,0.9999269034619644,,250,0.7397936319019545,0.7372065640321417
176,-/,1.6034297259196881,0,0,1.163131522260011e-06,0.0,0.9999988368684778,1.0,,250,0,0.0
179,b/,5.344765753065627,0,0,3.877105074200037e-06,0.0,0.9999961228949258,1.0,,250,0,0.0
1416,+/,1.0689531506131256,0,0,7.754210148400075e-07,0.0,0.9999992245789852,1.0,SP_0001,250,0,0.0
1883,+/,0.5344765753065628,0,0,3.8771050742000373e-07,0.0,0.9999996122894926,1.0,SP_0002,250,0,0.0
2891,-/,0.5344765753065628,0,0,3.8771050742000373e-07,0.0,0.9999996122894926,1.0,SP_0003,250,0,0.0
2920,+/b,1.0689531506131256,69.76170247459895,65.26170247459893,7.754210148400075e-07,5.0605295563040135e-05,0.9999992245789852,0.999949394704437,SP_0003,250,1.7567730683204659,1.750629610248355
$
$ head Fitness/TIGR4-T1-TIGR4-bioT4-1-norm-info.txt
# blank out of 48: 0.5625
blanks: 0.5625
total: 1378545.5
refname: NC_003028
SP_0017 0 14.9653441086
SP_0130 0 56.6545169825
SP_0130 0 16.5687738345
SP_0130 0 94.6023538293
SP_0130 0 10.6895315061
SP_0130 0.882472048814 59.326899859
```

Once finished with all fitness processing, you can also get sets of aggregated results over the fitness comparisons.  This is accomplised by using the Perl `aggregate.pl` script. Once again there are various switches and these can all be seen by running `aggregate.pl` with no arguments.


```sh
$ perl aggregate.pl

Usage: ./aggregate.pl -o outfile (options) file1 file2 file3...

Option List:

 -o     Output file for aggregated data. (Required)
 -c     Check for missing genes in the data set - provide a reference genome in
        genbank format. Missing genes will be sent to stdout.
 -m     Place a mark in an extra column for this set of genes. Provide a file
        with a list of genes seperated by newlines.
 -x     Cutoff: Don't include fitness scores with average counts (c1+c2)/2 < x (default: 0)
 -b     Blanks: Exclude -b % of blank fitness scores (scores where c2 = 0) (default: 0 = 0%)
 -w     Use weighted algorithm to calculate averages, variance, sd, se
 -l     Weight ceiling: maximum value to use as a weight (default: 999,999)
```

To finish running example, we will perform an aggregation over the finess results we calculated above.

```sh
$ perl aggregate.pl -m /Refs/NormGenes/NC_003028.txt \
?                   -w 1 -x 10 -l 50 -b 0 \
?                   -c /Refs/NC_003028.gbk \
?                   -o Aggrs/TIGR4-T1-TIGR4-bioT4-1.csv Fitness/TIGR4-T1-TIGR4-bioT4-1.csv
#
# lots of progress output. you can suppress this by adding `>> aggr.log 2>&1`
# to the end of the above command and the output will be saved
# in `aggr.log` (this assumes you are using bash command line)
#
$ head Aggrs/TIGR4-T1-TIGR4-bioT4-1.csv
locus,mean,var,sd,se,gene,Total,Blank,Not Blank,Blank Removed,M
SP_0001,0.10,0.10,X,X,,,,
SP_0002,0.10,0.10,X,X,,,,
SP_0003,1.75062961024835,0,0,0,,1,0,1,0
SP_0004,0.660232214169511,0,0,0,,1,0,1,0
SP_0005,0.10,0.10,X,X,,,,
SP_0006,0.913548484095829,0.18352130132045,0.428393862374859,0.107098465593715,,16,3,13,0
SP_0007,0.10,0.10,X,X,,,,
SP_0008,0.10,0.10,X,X,,,,
SP_0009,0.10,0.10,X,X,,,,
```


## WG-Seq breseq

The **breseq** [manual](https://gensoft.pasteur.fr/docs/breseq/0.35.0/) is very complete and detailed and should be the primary reference for its use.  Here we simply provide information on the switches and values used in the TVO Lab's use of breseq.

Here is an example run on non-clonal samples.

```sh
$ breseq -p -j 16
>        -r /Refs/NC_003028.gbk \
>        -o Out/T4t30NDCd20pop2 \
>        Samples/T4t30NDCd20pop2_S31_R1_001-qc14.fastq.gz \
>        Samples/T4t30NDCd20pop2_S31_R2_001-qc14.fastq.gz
#
# Lots of progress output
# this actually takes quite a while to run
# long run times are very typical
#
$ ls -l Out/T4t30NDCd20pop2/
total 40
drwxrwsr-x 2 anthonyj aerobio 4096 Dec 11 22:30 01_sequence_conversion
drwxrwsr-x 2 anthonyj aerobio 4096 Dec 11 22:30 02_reference_alignment
drwxrwsr-x 2 anthonyj aerobio 4096 Dec 11 22:38 03_candidate_junctions
drwxrwsr-x 2 anthonyj aerobio 4096 Dec 11 22:29 04_candidate_junction_alignment
drwxrwsr-x 2 anthonyj aerobio 4096 Dec 11 22:38 05_alignment_correction
drwxrwsr-x 2 anthonyj aerobio 4096 Dec 11 22:38 06_bam
drwxrwsr-x 2 anthonyj aerobio 4096 Dec 11 22:32 07_error_calibration
drwxrwsr-x 2 anthonyj aerobio 4096 Dec 11 22:38 08_mutation_identification
drwxrwsr-x 2 anthonyj aerobio 4096 Dec 11 22:38 data
drwxrwsr-x 4 anthonyj aerobio 4096 Dec 11 22:38 output
```

Most of the switches are self explanatory, and detailed explanations are given in the breseq manual. They are recapped here:

* -p The sample is not clonal. Predict polymorphic (mixed) mutations.
* -j Use the given value for number of CPU threads in parallelization
* -r Reference genome, typically in GBFF (GBK) format
* -o Output directory for the analysis data

If the samples *are* clonal, then there is another switch you should consider using:

* -l The value given (an integer) limits the number of sample reads per base within the reference sequences (for example CDSs). Hence, this effectively subsets the total reads analyzed by the computation.  The breseq authors indicate a value between 60 and 120 markedly speed up the run while maintaining sensitivity for clonal samples. Also, clearly, if your samples are clonal the `-p` switch should *not* be used.

The important output is in the subdirectory `output`:

```sh
$ ls -l Out/T4t30NDCd20pop2/output
total 120
drwxrwsr-x 2 anthonyj aerobio  4096 Dec 11 22:32 calibration
drwxrwsr-x 2 anthonyj aerobio  4096 Dec 11 22:38 evidence
-rw-rw-rw- 1 anthonyj aerobio 33698 Dec 11 22:38 index.html ## <-- Main starting point
-rw-rw-rw- 1 anthonyj aerobio   187 Dec 11 22:26 log.txt
-rw-rw-rw- 1 anthonyj aerobio  5775 Dec 11 22:38 marginal.html
-rw-rw-rw- 1 anthonyj aerobio  3938 Dec 11 22:38 output.done
-rw-rw-rw- 1 anthonyj aerobio 33338 Dec 11 22:38 output.gd
-rw-rw-rw- 1 anthonyj aerobio  3023 Dec 11 22:38 output.vcf
-rw-rw-rw- 1 anthonyj aerobio 11152 Dec 11 22:38 summary.html
-rw-rw-rw- 1 anthonyj aerobio  7537 Dec 11 22:38 summary.json
```

The `index.html` start page describes the predicted mutations and also evidence for mutations that breseq could not resolve into mutational events. For the details, see [breseq output](https://gensoft.pasteur.fr/docs/breseq/0.35.0/output.html#output-format)



## dualRNA-Seq

dualRNA-Seq involves the simultaneous RNA-Seq analysis of a bacterial pathogen and its infected host. It incorporates both eukaryote and bacterial reads per sequencer output sample involving the same conditions. It is basically the same as RNA-Seq with a few simple differences:

1. There are no reads with internal barcodes and thus no demultiplexing needs to be done to the sequencer output fastqs.
2. You will want to use STAR for the eukaryote alignments.
3. Generally you have paired end reads, so you need to inform featureCounts of this.

Everything else is the same as for RNA-Seq.

There are a truly large number of switches for STAR and the [official manual](https://github.com/alexdobin/STAR/blob/master/doc/STARmanual.pdf) should be the primary reference. However, generally, the following should do well for most needs:

* --genomeDir : The STAR index built from the genome fasta (see Index generation above).
* --runThreadN : Number of cores to use in parallelization.
* --readFilesCommand : Run the given command on input before processing. This is very useful for cases where your input files are gzipped.
* --readFilesIn : Space separated list of input fastq files
* --outSAMtype : Used to specify format of main output (alignments)
* --outFileNamePrefix : Used to provide a prefix for the output file names.  If you do not use streaming, these will include the alignment output.
* ----outStd : Alignment output is written to standard out. The value given indicates the output format.

Example using STAR:

```sh
$ STAR --genomeDir /Refs/STARindex/GRCm38_mm10 \
>      --runThreadN 24 \
>      --readFilesCommand zcat \
>      --readFilesIn Samples/E014M21/mouse-1E7-a-AATTGA-R1.fastq.gz \
>                    Samples/E014M21/mouse-1E7-a-AATTGA-R2.fastq.gz \
>      --outSAMtype BAM SortedByCoordinate \
>      --outFileNamePrefix STAR/mouse-1E7-a-
#
# May take some time, but generally, STAR is quite fast
#
$ ls -l Star/mouse-1E7-a*
-rw-rw-r-- 1 anthonyj aerobio 2497817409 Aug 14  2023 STAR/mouse-1E7-a-Aligned.sortedByCoordinate.out.bam
-rw-rw-r-- 1 anthonyj aerobio       2028 Aug 14  2023 STAR/mouse-1E7-a-Log.final.out
-rw-rw-r-- 1 anthonyj aerobio       9301 Aug 14  2023 STAR/mouse-1E7-a-Log.out
-rw-rw-r-- 1 anthonyj aerobio       1072 Aug 14  2023 STAR/mouse-1E7-a-Log.progress.out
-rw-rw-r-- 1 anthonyj aerobio        754 Aug 14  2023 STAR/mouse-1E7-a-Log.std.out
-rw-rw-r-- 1 anthonyj aerobio    5769073 Aug 14  2023 STAR/mouse-1E7-a-SJ.out.tab

```

The output BAMs generated can now be processed just like for RNA-Seq.  The only difference (as in this case) is if you have paired end reads.  In that case you will need to give `featureCounts` the `-p` switch so that it can properly account for this.

```sh
$ featureCounts -a /Refs/GRCm38_mm10.gtf \
>               -o Fcnts/mouse-1E7-mouse-UN.csv \
>               -p \ # indicates to featureCounts there are PE reads
>               -t CDS \
>               <list of bams to process>
```



## TRADis-Seq

TRADis-Seq is a slight variation of Tn-Seq, where the sequencer sample fastqs are not multiplexed but still need a piece of pre-processing.  The gDNA in the reads to be analyzed first needs to be extracted into corresponding fastqs. This is done by first finding a pattern marker sequence in the reads and then a length of gDNA downstream of it is retrieved. The pattern and length *may* vary among experiments.

Here (TVO Lab), the pattern sequence is `CCGGGGACTTATCAGCCAACCTGT` and the length is 24. One way to find the pattern and extract the gDNA is to use the `seqkit` tool suite. There are three basic steps:

1. Find all reads that have the pattern
2. Locate the pattern within those reads
3. Extract the sequence downstream of the pattern

These three basic steps correspond to the `grep`, `locate`, and `subseq` subcommands of `seqkit`.

```sh
# Get the reads with the pattern and pipe to locator
# Write the location results in BED file format to p.bed
#
$ seqkit grep -p CCGGGGACTTATCAGCCAACCTGT \
>             -r -s ../Data/Samples/WTAT0_S8_R1_001.fastq.gz \
>  | seqkit locate -p CCGGGGACTTATCAGCCAACCTGT --bed > p.bed
#
# The BED file can be used as input to the subseq command
#
$ zcat ../Data/Samples/WTAT0_S8_R1_001.fastq.gz \
>  | seqkit subseq --bed p.bed -d 24 -f \
>  | gzip --stdout > s.fastq.gz
#
# The -d 24 says  pull 24 bases downstream of locations in p.bed
# The -f says to only write that sequence to output
# Not necessarily needed, but here gzip these. Lastly, write to s.fastq.gz
```

The set of fastq(.gz) files generated can now be used as normal Tn-Seq input.



# Aerobio

This section discusses the process of installing the [Aerobio](https://github.com/jsa-aerial/aerobio/blob/master/doc/user-guide.md) HTS analysis system. There are several reasons for why you will want to install Aerobio and use it for your analyses.

* There are more analyses available out of the box than the by [hand analyses](#flows) alone (such as RBTn-Seq)

* There auxilliary support processing jobs to help automate analyses (for example, automatic referene generation)

* The performance of any analysis will be much higher due to the use of streaming pipelines.

* You have data scaling concerns.
  - Very large datasets (100s GB) can be processed with much higher performance.  Aerobio makes use of extensive and tuned parallelization in how it performs the analyses.
  - Large numbers of samples (dozens to hundreds). Dealing with this by hand can be very cumbersome and time consuming. Without needing to write any custom scripts to help, Aerobio automatically splits samples into optimally sized chunks and runs the analyses for you.

* Canonical locations and names can help a great deal in organizing your work and enabling you to always go back and find other details when needed.  Aerobio uses and enforces canonical naming and locations for all analysis input and output.  This is achieved by the server [config file](#config) and the format of entries in the [spread sheets](https://github.com/jsa-aerial/aerobio/blob/master/doc/user-guide.md#experiment-definitions) used to define experiment layouts.


## Uberjar

Aerobio comes as a self installing 'uberjar'. This uses [Java](#java) to run it in both the server and installation mode. You can download the jar from this [link](https://drive.google.com/file/d/1WG-00fGkdb6_RCIZv9eLBR3nl860HwKG/view?usp=sharing).


* Download the uberjar from the link.  When you go to this link it will pop up a window that will look something like this:

![no preview](../resources/public/images/small-aerobio-no-preview.png?raw=true)

Click the download button. Another tab should open with something like this in it:

![download anyway](../resources/public/images/small-aerobio-download-anyway.png?raw=true)

Click the `download anyway` button. It should then download the jar file.

* The jar file will be `aerobio-x.y.z-standalone.jar`, where `x` is the *major* version number (like 3), `y` is the *minor* version number (like 0), and `z` is a *patch* version number (like 0). Move it from the downloads area to your home directory.

* Open a command window and make sure you are in your home directory.

* Make sure you have [Java](#java) installed. You can easily check this by issuing `java -version` at your command prompt.

* Install Aerobio by issuing `java -jar aerobio-x.y.z-standalone.jar --install`. This will install Aerobio in your home directory in the directory `.aerobio`. If you list that directory it will look like this:

```sh
drwxrwxr-x 2 jsa jsa   4096 Feb 11 14:23 cache/
-rw-rw-r-- 1 jsa jsa   2521 Feb 11 14:23 config.clj
drwxrwxr-x 2 jsa jsa   4096 Feb 11 14:23 Jobs/
drwxrwxr-x 2 jsa jsa   4096 Feb 11 14:23 NS/
drwxrwxr-x 2 jsa jsa   4096 Feb 11 14:23 pipes/
drwxrwxr-x 2 jsa jsa   4096 Feb 11 14:23 Scripts/
drwxrwxr-x 2 jsa jsa   4096 Feb 11 14:23 services/
drwxrwxr-x 2 jsa jsa   4096 Feb 11 14:23 Support/

```

## Aerobio command

The `aerobio` command line command is found in the `~/.aerobio/Support` directory.[^1] The installation process marks it as an executable. On POSIX systems (Unix, Linux, OSX, etc), this makes it a directly executable script. On Windows, you will need to invoke it via your `python` installation.

As a Python script, it requires Python 3 plus three additional packages: `trio`, `trio-websocket` and `msgpack` (see [Miscellaneous Python packages](#miscellaneous-python-packages) under [tools](#tools)).

* Install the three packages via the [pip](#scripting-languages) command. At your command line prompt issue these three commands

```sh
pip install trio
pip install trio-websocket
pip install msgpack
```

* You *should* now be able to run the command. Assuming you have not moved the command yet, you can try it by:

* POSIX (Unix, Linux, OSX, etc): `~/.aerobio/Support/aerobio`
* Windows: `python \Users\<your home directory name>\.aerobio\Support\aerobio`

This will dump a large help file to your screen. If you get errors about other missing packages, you will need to `pip install` those and try again. However, on most typical Python installations, the above noted packages should be the only extra ones you need to install.

## Aerobio server




## Config file



[^1]: Here we are using the standard POSIX tilde syntax to indicate the home directory. For Windows users, the '~' is not legal, but you should read it here as your homedirectory.
