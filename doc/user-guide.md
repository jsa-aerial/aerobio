**Aerobio** is a system for high throughput sequencing.  Create services for individual command line tools (STAR, samtools, scripts, et al), functions (any Clojure function), and compound services as computation graphs composed of other services. Create jobs as program graphs composed of services to provide any pipeline capability.  This guide is primarily intended for those accessing analyzes by the various provided jobs for RNA-Seq, dualRNA-Seq, Tn-Seq, WGSeq, scRNA-Seq, scWG-Seq, and Term-Seq.  There is also a companion guide for those creating new services and jobs.

Table of Contents
=================

* [Overview](#overview)
* [Basic background and terminology](#basic-background-and-terminology)
   * [Job Phases](#job-phases)
   * [Supported Experiment Types](#supported-experiment-types)
   * [Experiment ID](#experiment-id)
* [Compute Resources](#compute-resources)
   * [VM](#vm)
   * [System disk](#system-disk)
   * [Persistent disks](#persistent-disks)
   * [GCP Storage Bucket](#gcp-storage-bucket)
* [gcloud cli](#gcloud-cli)
   * [Installation](#installation)
   * [Perform gcloud init](#perform-gcloud-init)
   * [VM login](#vm-login)
   * [VM scp](#vm-scp)
   * [SSH tunnels](#ssh-tunnels)
   * [Copying from the bucket](#copying-from-the-bucket)
* [Aerobio command](#aerobio-command)
   * [Commands](#commands)
   * [Analysis Phases](#analysis-phases)
   * [fdsfds](#fdsfds)
* [Experiment Input Structure](#experiment-input-structure)
   * [Direct Sequencer Output](#direct-sequencer-output)
   * [Fastq Files](#fastq-files)
   * [Experiment specifications](#experiment-specifications)
      * [Experiment definitions](#experiment-definitions)
         * [SampleSheet.csv](#samplesheetcsv)
            * [V1 specifics](#v1-specifics)
            * [V2 specifics](#v2-specifics)
            * [Sample Sheet Examples](#sample-sheet-examples)
         * [Exp-SampleSheet.csv](#exp-samplesheetcsv)
            * [Experiment type information](#experiment-type-information)
            * [Genome official name, lab id cross reference](#genome-official-name-lab-id-cross-reference)
            * [Sequencer samples, experiment multiplexed read linkage](#sequencer-samples-experiment-multiplexed-read-linkage)
            * [Experiment Sheet Examples](#experiment-sheet-examples)
         * [ComparisonSheet.csv](#comparisonsheetcsv)
      * [Experiment protocols](#experiment-protocols)
      * [Command configuration](#command-configuration)
* [Experiment Types](#experiment-types)
   * [RNA-Seq](#rna-seq)
   * [dualRNA-Seq](#dualrna-seq)
   * [Tn-Seq](#tn-seq)
   * [WG-Seq](#wg-seq)
   * [Term-Seq](#term-seq)
   * [scRNA-Seq](#scrna-seq)
   * [scWG-Seq](#scwg-seq)

<!-- Created by https://github.com/ekalinin/github-markdown-toc -->

# Overview

**Aerobio** is a system for creating _services_ and connecting them together to form _jobs_.  A _service_ defines a piece of computation and may be _implemented_ by external tooling or functions (code specific to the computation involved). Services can also be _compound_ (a computation graph of other services).  A _job_ defines a program as a directed acyclical graph (DAG) composed of service nodes and data flow edges. Nodes may have multiple inputs and multiple outputs. Both services and jobs are specified entirely by data (EDN or JSON). Jobs are _realized_ by instantiating the node processing as (OS level) threads of execution and the data flow connections as streaming aysnchronous channels. A realized job is roughly equivalent to the notion of a 'pipeline'.

While the system is general enough to apply to a range of domains, the intended target is specifically oriented toward high throughput sequencing (HTS) analysis of RNA-Seq, dualRNA-Seq, scRNA-Seq, scWG-Seq, Tn-SEq, WG-Seq, and Term-Seq data sets. This is realized by supporting the specification of experiment data processing by sets of spreadsheets. These spreadsheets are simple in structure and use the terminology of biologists. They are used to automatically construct jobs that will perform all necessary analysis for all samples and all comparisons.


# Basic background and terminology

HTS analyses are based on the specification of _data flows_ which describe what data to process when and where the results of that processing need to go for any next step processing. So, data flows are abstractions that describe what needs to be done.  These descriptions need to be turned into actual processing and in Aerobio this is done by _implementing_ them as _jobs_.  In the HTS processing in Aerobio, a job corresponds to a specific chunk of processing.  What these chunks are and what they do depends on the particular experiment _type_.

In all cases, the data flow across the processing is done via streaming channels between each piece of processing.  This has several benefits over other available pipeline frameworks.  The most important of these being

1. Streaming enables the processing to run in parallel.
2. Streaming eliminates the need (and burden) of intermediate files.


## Job Phases

While a single data flow from sequencer output all the way through to an initial analysis (such as DGE or fitness) could be a single job, it turns out that it is more effective to separate this into chunks of processing called _phases_.  There are several advantages ranging from load balancing to mitigating wasted processing due to up stream problems. Generally, each phase of an HTS analysis run corresponds to a chunk of processing that results in direcly usable artifacts. 


## Supported Experiment Types 

There are several [experiment types](#experiment-types) currently supported and some new ones are in the process of being completed.  The current set of supported experiment types is

* [RNA-Seq](#rna-seq)
* [dualRNA-Seq](#dualrna-seq)
* [Tn-Seq](#tn-seq)
* [WG-Seq](#wg-seq)
* [Term-Seq](#term-seq)

New experiment type job flows being completed are

* [scRNA-Seq](#scrna-seq)
* [scWG-Seq](#scwg-seq)


## Experiment ID

jfkdsl






# Compute Resources


Currently Aerobio and all the necessary tools it makes use of for HTS analysis are hosted on a Google Cloud Platform (GCP) Virtual Machine (VM).  To access these resources you need to install and use [gcloud cli](#gcloud-cli) which is detailed below.  Here is a simple graphic of the current GCP resources:

![VMs and disks](../resources/public/images/vm-disks.png?raw=true)


## VM
The VM is an AMD Milan 64 vCPU 256GB RAM configuration, running Ubuntu 22.04 OS.


## System disk

The system disk is a 500GB SSD that has the tools, software (including Aerobio) and the main database.  **NOTE**: It is _very_ important to never fill up the system disk.  Thus, while your login home directories are on the system disk, *never* put or generate any large amount of data under your home directory.

The database (DB) is a MySql relational data base (RDB) that makes use of the BioSql schema and has:

* RefSeq data for human, mouse, and large numbers of bacteria. We can also add annotation datasets (via genbank files) for any required genomes not yet loaded.

* Experiment definitions (meta data)

* High level analysis output


## Persistent disks

The persistent disks provide the storage for experiment input and analysis output data.  Additionally they contain a wide range of reference input data for performing analyses and scratch space for individual work.  Notable predefined directories include:

* `/ExpIn` : experiment home directories containing experiment input dxta (sequencer output or fastq files) and experiment definitions. See [here](#experiment-input-structure)

* `/ExpOut` : experiment output areas with same names as their home directories under `/ExpIn` and containing all analysis output.

* `/GBKs` : Genbank files used to load the DB and create genome fasta and reference data.

* `/Fastas` : Fasta files from genbank files

* `/Refs` : Reference data for genomes used in analyses.  This includes
  - Bowtie 1 and 2 indices
  - STAR indices
  - GTF annotation files

* `/Scratch` : general area for users doing adhoc individual processing.


## GCP Storage Bucket

In addition to the direct resources of the VM, there is also a GCP storage bucket associated with Aerobio.  This bucket is the primary way point for your sequencer output or direct fastq files to go into the [experiment input structure](#experiment-input-structure) for your experiment.

There are two access points.  One for browser level interaction and the other for [gcloud](#gcloud-cli) command line interaction.

* Browser [Cloud Console URL](https://console.cloud.google.com/storage/browser/aerobio1-expcache)

* gcloud gsutil URL : gs://aerobio1-expcache

When uploading to the bucket, you should always use a directory on the bucket, either previously created or a new directory you create when copying your new data.  **Never** put sequencer or fastq file data directly into the bucket at top level.





# gcloud cli

## Installation

1. Go to [gcloud install](https://cloud.google.com/sdk/docs/install)
2. Select platform and download
3. Follow instructions
   - you will need to be in a terminal session

The instructions closely follow the actual flow.  You need at least python 3.9 installed.

## Perform gcloud init

1. Go into install directory as indicated in instructions
2. Issue command `gcloud init`
   - login: Y
     - Your browser will open a new tab/window.  You should have some accounts to pick from to log in with.
     - Use the account that was setup for your use on the VM
     - Allow access
   - Back at terminal command line (Various output with `authenticated` at the end)
     - Pick a project to use
       In the list there should be an entry for `aerobio-at-broad`.  Choose it.
     - Default region and zone? Y
       Pick the number for `us-central1-c`.  Various output and should finish with a few confirmations as to what you selected.


## VM login

Logging into the VM is done via gcloud by means of an ssh tunneling protocol Google calls _Identity Aware Proxy_ or IAP.  This eliminates the need for a VPN and makes the overall interaction with the VM simpler and more seamless.  IAP is also used to establish tunnels to webservers in a similar way.

To login, issue the following command

```shell
gcloud compute ssh --zone us-central1-c aerobio-1 --project aerobio-at-broad --tunnel-through-iap
```

## VM scp

It is also possible to copy things to and from your local machine to the storage on the VM. If you do copy something _to_ the VM *never* copy to your home directory!  You should only copy to a directory under `/Scratch`.

To copy, the gcloud command is again used as follows:

* Copying from your local machine to the VM:

```shell
gcloud compute scp <path-to-local-file> <your account>@aerobio-1:/Scratch/<path-to-VM-file>

# Directories can also be copied by using the recurse option
#
gcloud compute scp --recurse <path-to-local-dir> <your account>@aerobio-1:/Scratch/<path-to-VM-dir>
```

* Copy from VM to your local machine:

To copy a file or directory from the VM to your local machine, you use the same commands but put the path to the VM resource first and then your local path second:

```shell
gcloud compute scp  <your account>@aerobio-1:<path-to-VM-file> <path-to-local-file>
```

_NOTE_: you can copy any file/directory you have permissions to on the VM to your local machine; not just those in `/Scratch`


## SSH tunnels

There are some services on the VM that are delivered by means of webserver based applications.  For these, you will be given a port on the VM where the server is listening (`vmport`) and a suggested _local_ port (`lclport`) to tunnel through to the VM port.  To activate this you again use the gcloud command with IAP tunneling.  Both the `vmport` and `lclport` will be _numbers_!  These names are used here simply as placeholders.

The command to use will be:


```shell
gcloud compute start-iap-tunnel aerobio-1 vmport --local-host-port=localhost:lclport --zone=us-central1-c &
```

This will start a daemon that runs in the background listening for any connection request.  Such a connection request would be done by opening a browser tab/window and putting `localhost:lclport` in as the web address to visit.


## Copying from the bucket

You can also use the gcloud command to copy data from [bucket](#gcp-storage-bucket) to `/ExpIn` as part of [setting up](#experiment-input-structure) an experiment's home directory. The command will make use of `gisutil URL` of the bucket plus and subdirectory path to the data.  You can always get a copy of the full path to the data (including the gis bucket prefix) from the browser cloud console for the bucket.  The command will then be of the form:

```shell
# For files
#
gcloud storage cp <bucket-path-to-file> /ExpIn/<path-to-put-data>

# For directories
#
gcloud storage cp -r <bucket-path-to-directory> /ExpIn/<path-to-put-data>

```




# Aerobio command

Currently, the primary means of accessing the aerobio server for running [jobs](#(job-phases) is the `aerobio` command line tool.  To use it you need a terminal shell session (CLI) by [logging into](#vm-login) a VM.  Once on a VM in a terminal session, the command will be directly available at the command line. Issuing the command with no parameters will issue a synopsis of its usage (short help).

In the following,

* Elements enclosed within angle brackets, `<` and `>`, and separated by bars, `|`, indicate one and only one must be given. Ex: `<a | b>` means exactly _one_ of `a` or `b` must be given.

* Elements enclosed within braces, `{` and `}` and separated by bars, `|`, indicate at most one can be given, but none may be given - they are optional. Ex: `{a | b}` means exactly one of `a` or `b` or nothing may be given.


The general format of the `aerobio` command for running the various supported HTS jobs is:

```shell
aerobio <cmd> <action | compfile | aggrfile> {replicates | combined} <eid>
```

## Commands

`cmd` is one of the following values

   * `run` : this is the primary command for Aerobio across all types of jobs, not just the supported HTS analysis jobs.  Here, `run` expects a standard set of jobs for the typical [phases](#analysis-phases) of analysis per experiment. `run` takes a phase designator and the [experiment's ID (EID)](#experiment-id). Example command:

```shell
aerobio run phase-0 240216_Rosch_BHN97
```

   * `check` : This command is typically used before initiating any job runs.  It invokes the validator which performs integrity checks on the various aspects of the [experiment structure and definitions](#experiment-input-structure).  It reports  any errors found in detail and in a quite experimenter friendly format. `check` takes only an [experiment id (EID)](#experiment-id). Example command:

```shell
aerobio check 240216_Rosch_BHN97
```

   * `compare` :

   * `xcompare` :

   * `aggregate` :


## Analysis Phases


## fdsfds



# Experiment Input Structure

The input for an experiment's automated processing starts with the creation of a directory under [ExpIn](#persistent-disks) which will function as the _experiment's home directory_.  This directory is also known as the EID (experiment identifier) of the experiment. The directory will contain the starting input data.  This will be either the direct sequencer output or a set of fastq files that have been "pre-converted" from a direct sequencer output.  Which of these two is available will depend on the deliverables of your sequencing facility.  This directory will also be where you put the [experiment specification](#experiment-specifications) files defining the experiment analysis. 

## Direct Sequencer Output

If you have the direct sequencer output directory it can be the directory for the experiment under `/ExpIn`.  With this directory in the [bucket](#gcp-storage-bucket), you can copy the directory to `/ExpIn` using [gcloud cli](#copying-from-the-bucket).  For example, let's say your sequencer output directory in the bucket  is `240411_example_exp`.  Then you could perform the transfer with:

```shell
gcloud storage cp -r gs://aerobio1-expcache/2400411_example_exp /ExpIn
```

Make sure that the SampleSheet.csv supplied at the top level of this directory is in the format expected by Aerobio.  Specifically, that the `[Data]` section conforms to the expected format.


## Fastq Files

If you have pre-converted fastq files, you will need to create a directory for the experiment under `/ExpIn` as a first step. It is best if you make sure to include the date (like in typical sequencer output directories) and relevant information for quick identification of the experiment.  The format should be `YYMMDD_descriptive_info_separated_with_underbars`.

In order for this variation of input to be compatible with the direct sequencer output version, you will next need to create the expected path to the input fastq files.  This path will be `Data/Intensities/BaseCalls` directly under the experiments home directory.  For example, let's say your EID is `240411_example_exp`, then you could create the necessary path with:

```shell
mkdir -p /ExpIn/240411_example_exp/Data/Intensities/BaseCalls

# Or if you are in your EID
#
cd /ExpIn/240411_example_exp
mkdir -p Data/Intensities/BaseCalls
```

Once you have this path created, you then will need to copy your fastq files into that directory.  Once again, it is likely your input is in the bucket and that you should use `gcloud` to [copy](#copying-from-the-bucket) your fastq files into the `BaseCalls` directory.  For example,

```shell
gcloud storage cp gs://aerobio1-expcache/2400411_example_exp_fastqs/*.gz /ExpIn/2400411_example_exp/Data/Intensities/BaseCalls
```


## Experiment specifications

There are three kinds of specifications for defining the data analysis processing for an [experiment type](#supported-experiment-types):

* Experiment definition sheets

* Experiment sequence protocol layouts

* Command tool switch/parameter 


### Experiment definitions

These comprise three tabular specificatins, typically built as spreadsheets (in Excel or similar) and saved as CSV files in DOS or Linux standard line terminatioin format.  The key is to ensure lines have line feed characters (LF) as part of their termination.  Apple standard, Macbook et al., does **not** include an LF as a line terminator.  So, if you are working on Apple devices you need to ensure that you save your spreadsheet defined files in DOS or similar format.  If you do not do this, the Aerobio validator will flat your definition CSV sheets as having an erroneous format.

The three definition sheets and their **required** names are:

#### SampleSheet.csv

This is the Illumina SampleSheet.csv file that is included in any direct sequencer output directories.  It is typically defined as part of your description of  your experiment when submitting your data for sequencing.

In Aerobio, the sample sheet is primarily used to associate a sequencer sample name with Illumina index (I7 and I5) barcodes.  I7 uses a column name of `index` while I5 uses `index2`.

This information is listed in the **[Data]** section of V1 sample sheets and in **[BCLConvert_Data]** for V2 sample sheets.  For Aerobio, you only need to include these sections. However, especially for V2 sheets, `bcl-convert` processing typically _needs_ several more sections.

In the following, `{abcxyz}` indicates that `abcxyz` is *optional*


##### V1 specifics

1. Basic form

| [Data]    |             |         |          |
| --------- | ----------- | ------- | ---------|
| Sample_ID | Sample_Name | index   | {index2} |
| ...       | ...         | ...     | {...}    |


2. Both `Sample_ID` and `Sample_Name` need to be included

3. `Sample_ID` values **must** equal corresponding `Sample_Name` values

4. 'index' is the I7 index for the sample

5. 'index2', if given, is the I5 index of the sample

6. THe barcodes for I7 and I5 _must_ be composed of the uppercase letters 'A', 'T', 'G', 'C.


##### V2 specifics

1. Basic form

* V2 sample sheet example (scWGS no I7 or I5)

| [BCLConvert_Data] |                   |         |          |
| ----------------- | ----------------- | ------- | -------- |
| {Lane}            | Sample_ID         | {index} | {index2} |
| ...               | ...               | ...     | {...}    |


2. There is **only** the `Sample_ID` field (no `Sample_Name`)

3. The `Lane` field is typically included by sequencing facilities

4. Both indices are optional, but typically at least `index` is given.  In scWGS work, neither index may appear, with read descriptions given in **[BCLConvert_Settings]** section via `OverrideCycles` (see Illumina [V2 reference](https://support-docs.illumina.com/SHARE/SampleSheetv2/Content/SHARE/FrontPages/SampleSheetv2.htm)

5. 'index' is the I7 index for the sample. 'index2', if given, is the I5 index of the sample

6. THe barcodes for I7 and I5 _must_ be composed of the uppercase letters 'A', 'T', 'G', 'C.



##### Sample Sheet Examples

* V1 sample sheet example with only I7 (typical single end read)

| [Data]    |             |         |
| --------- | ----------- | ------- |
| Sample_ID | Sample_Name | index   |
| P1M6BC01  | P1M6BC01    | ATCACG  |
| P1M6BC02  | P1M6BC02    | CGATCT  |
| P1M6BC03  | P1M6BC03    | TTAGGC  |
| P1M6BC04  | P1M6BC04    | TGACCA  |
|           |             |         |



* V1 sample sheet example with I7 and I5 (typical paired end read)

| [Data]    |             |          |          |
| --------- | ----------- | -------- | -------- |
| Sample_ID | Sample_Name | index    | index2   |
| E014M11   | E014M11     | CGGCTATG | CTTCGCCT |
| E014M12   | E014M12     | TCCGCGAA | CTTCGCCT |
| E014M13   | E014M13     | TCTCGCGC | CTTCGCCT |
| ...       | ...         | ...      | ...      |
| E014M19   | E014M19     | ATTACTCG | TAAGATTA |
| E014M20   | E014M20     | TCCGGAGA | TAAGATTA |
| E014M21   | E014M21     | CGCTCATT | TAAGATTA |
| ...       | ...         | ...      | ...      |
| E016M36   | E016M36     | ATTACTCG | ACGTCCTG |
| E016M37   | E016M37     | TCCGGAGA | ACGTCCTG |
| E016M38   | E016M38     | CGCTCATT | ACGTCCTG |
| ...       | ...         | ...      | ...      |


* V2 sample sheet example (scWGS not I7 or I5)

| [BCLConvert_Data] |                   |
| ----------------- | ----------------- |
| Lane              | Sample_ID         |
| 1                 | samp1_scWGS_resub |
| 2                 | samp2_scWGS_resub |


#### Exp-SampleSheet.csv

This sheet is specific to Aerobio.  It provides the information that describes the type of experiment the run is, what reference genomes are involved, and how the reads in the sequencer output samples are linked with the experiment sequence reads within them.  The former are the output from a converter (`bcl2fastq` or `bcl-convert`) and are typically determined by the _I7_ and/or _I5_ indices.  The latter are differentiated within a sequencer sample by experiment specific barcodes and are typically sample replicates but may be other sequence types such as those differentiating between species in dual RNA-Seq experiments.

There are three sections to this sheet. The experiment type section, the lab id/name official genome name cross reference section, and the sequence sample index experiment barcode linkage section.

**_NOTE_** : Each section _must_ be separated from each other by at least one empty row!

##### Experiment type information

1. This section is only one row and must be the first row in the sheet.

Format

| Experiment type   | experimenter | experiment description | {exp params} |
| ----------------- | -------------| ---------------------- | ------------ |


2. The experiment type is the most imortant field and _must_ be the [type](#supported-experiment-types) of the experiment to be analyzed. These are lowercase codes that _must_ be one of the following:

* [RNA-Seq](#rna-seq) : code must be `rnaseq`
* [dualRNA-Seq](#dualrna-seq) : code must be `dual-rnaseq`
* [Tn-Seq](#tn-seq) : code must be `tnseq`
* [WG-Seq](#wg-seq) : code must be `wgseq`
* [Term-Seq](#term-seq) : code must be `termseq`
* [scRNA-Seq](#scrna-seq) : code must be `scrnaseq`
* [scWG-Seq](#scwg-seq) : `scwgseq`

3. The experimenter field should be the name of the experimenter's user account on the VM

4. The experiment description should be a short description of what the experiment is about.

5. The optional `exp params` field provides further information for analysis


##### Genome official name, lab id cross reference

1. This section is for cross referencing official genome _locus names_ (ascension IDs) with informal laboratory IDs for the genome.  This supports the using of the latter IDs, which are typically more descriptive, in various configurations while ensuring that all processing uses the reference data obtained and generated from the official source.

Format

| n   | Official locus name | Lab ID |
| --- | ------------------- | ------ |

2. _n_ is an integer, the ordinal number of the row in the section (1, 2, ...)

3. The locus name is the name of the genome as given by NCBI or another official source.  It is the locus field value in the associated official genbank file. This name will be given to you.

4. The lab ID is an adhoc name, in general use or lab specific, that is more readily identified with the genome.  This name must *not* contain spaces (' '), or any punctuation (, ; - + / \ $ % # @ = ! & ? : | { } [ ]).


##### Sequencer samples, experiment multiplexed read linkage

First we define some terms that will be used here and in subsequent areas.

* _Sequencer output samples_ : These are the sets of reads defined by the I7 and I5 indices specified in the [sample sheet](#samplesheetcsv). They typically reside in one (if only R1 reads) or two (corresponding R1 and R2) fastq files as generated by a _no lane splitting_ conversion (via bcl2fastq, bcl-convert, et.al.) from direct sequencer [binary basecalls (BCL) fileas](https://www.illumina.com/informatics/sequencing-data-analysis/sequence-file-formats.html).

* _Experiment sub-reads_ : These reads constitute subsets within the sequencer samples. Generally, they are identified with a condition, replicate within a condition, organism, or some other experiment specific _attributes_. They are identified by barcodes attached to the relevant sequences.

* _Experiment barcodes_ : These are the barcodes attached to sequences to identify an experiment's various reads within the different sequencer samples. They pick out (or delineate) the various subsets of reads corresponding to conditions, replicates, species, etc.  They may or may not span samples.

Given the above, Aerobio needs a means by which it can pull out reads from samples that are specific to a particular experimental attribute and aggregate them into separate fastq files. That's what the read linkage section of the Exp-SampleSheet.csv is for.


1. This section specifies how experiment barcodes match up with each I7 index, or I7 and I5 index combination, that identifies a sequencer sample.  Additionally, it provides a short hand means of naming the resulting aggregated fastq files containing the reads with a given experiment barcode.

Format

There are two formats, depending on whether there is only an I7 index or an I7-I5 index combination for the sequencer sample


* I7 only

| n   | organism-condition-replicate | I7 index | ExpBC |
| --- | ---------------------------- | -------- | ----- |

* I7-I5 combination

| n   | organism-condition-replicate | I7 index | I5 index | ExpBC |
| --- | ---------------------------- | -------- | -------- | ----- |


2. _n_ is an integer, the ordinal number of the row in the section (1, 2, ...)


3. _organism-condition-replicate_ provides three pieces of information.

   * The _organism_.  This _must_ be one of the lab IDs provided in the [genome lab id xref](#genome-official-name-lab-id-cross-reference).

   * The _condition_.  This should be a short description of what the associated reads involve.  Some examples, "CIPT0" for ciprofloxacin at time 0 or "T4roth" for tigr4 in broth media. Again, this text must *not* contain spaces or any punctuation. The different 'samples' described are also known as [biological replicates](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4825082/#:~:text=Broadly%20speaking%2C%20biological%20replicates%20are,the%20measuring%20equipment%20and%20protocols).

   * The _replicate_.  This is an indicator of the different measurements of the condition.  These are also known as [technical replicates](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4825082/#:~:text=Broadly%20speaking%2C%20biological%20replicates%20are,the%20measuring%20equipment%20and%20protocols). Currently these indicators _must_ be a single digit [0-9] or single lower case letter [a-z]. Using digits and letters is **mutually exclusive**.  This also means there is currently a limit of a **maximum of  26 replicates** per organism-condition.  These constraints may be lifted in the future.


4. The I7 and I5 indices _must_ be one given for a sample in the [sample sheet](#samplesheetcsv).  When both are given, the I7-I5 combination _must_ be one that occurs on a single line of the sheet, i.e., identifies a _unique_ sample.

5. The ExpBC must match a barcode used in the experiment that _uniquely_ identifies a condition-replicate combination.

6. All barcodes _must_ be composed of the uppercase letters 'A', 'T', 'G', 'C.


##### Experiment Sheet Examples


* Tn-Seq experiment with two organisms, multiple conditions and technical reps

| tnseq | Suyen            | Biofilm   |        |
| ----- | ---------------- | --------- | ------ |
|       |                  |           |        |
| 1     | NC_003028        | TIGR4     |        |
| 2     | CP035236         | BHN97     |        |
|       |                  |           |        |
| **n** | **org-cond-rep** | **i7**        | **ExpBC**  |
| 1     | TIGR4-T1-1       | ATCACG    | ATGGCC |
| 2     | TIGR4-T1-2       | ATCACG    | CCTAAT |
| 3     | TIGR4-T1-3       | ATCACG    | TACCGG |
| 4     | TIGR4-bioT96-1   | ATCACG    | GGATTA |
| 5     | TIGR4-plankT96-1 | ATCACG    | CCTACC |
| 6     | TIGR4-bioT96-2   | ATCACG    | ATGGAT |
| 7     | TIGR4-plankT96-2 | ATCACG    | GGATGG |
| 8     | TIGR4-bioT96-3   | ATCACG    | TACCTA |
| 9     | TIGR4-plankT96-3 | ATCACG    | CCGGAT |
| 10    | BHN97-T1-1       | ATCACG    | ATTACC |
| 11    | BHN97-T1-2       | ATCACG    | GGCCTA |
| 12    | BHN97-T1-3       | ATCACG    | TAATGG |
| 13    | BHN97-T1-4       | ATCACG    | ACGAAT |
| ...   | ...              | ...       | ...    |


* dualRNA-Seq, single index => artificial ExpBC

| dual-rnaseq | karenzhu          | invivoRNASeqmastersheet | single-index |    |
| ----------- | ----------------- | ----------------------- | ------------ | --- |
|             |                   |                         |              |
| 1           | TVO_1901932       | PG13                    |              |
| 2           | NC_003028         | T4                      |              |
| 3           | GRCm38_mm10       | mouse                   |              |
| 4           | GRCh38_hsapienP14 | A549                    |              |
|             |                   |                         |              |
| **n** | **org-cond-rep** | **i7**  | **I5**      | **ExpBC**  |
| 1           | PG13-5E7-a | CGGCTATG | CTTCGCCT | AAAAAA |
| 2           | PG13-5E7-b | TCCGCGAA | CTTCGCCT | AAAAAT |
| 3           | PG13-5E7-c | TCTCGCGC | CTTCGCCT | AAAAAG |
| 4           | PG13-5E7-d | ATTACTCG | CTTCGCCT | AAAAAC |
| 5           | PG13-5E7-e | TCCGGAGA | CTTCGCCT | AAAATA |
| 6           | PG13-5E7-f | CGCTCATT | CTTCGCCT | AAAATT |
| 7           | PG13-5E7-g | GAGATTCC | CTTCGCCT | AAAATG |
| 8           | PG13-5E7-h | AGCGATAG | CTTCGCCT | AAAATC |
| 9           | PG13-5E7-i | ATTACTCG | TAAGATTA | AAAAGA |
| 10          | PG13-5E7-j | TCCGGAGA | TAAGATTA | AAAAGT |
| ...         | ...        | ...      | ...      | ...    |
| 39          | T4-T4MOI1-a | ATTACTCG | AGGCTATA | AAAGTG |
| 40          | T4-T4MOI5-a | TCCGGAGA | AGGCTATA | AAAGTC |
| 41          | T4-T4MOI10-a | CGCTCATT | AGGCTATA | AAAGGA |
| 42          | T4-T4MOI25-a | GAGATTCC | AGGCTATA | AAAGGT |
| ...         | ...        | ...      | ...      | ...    |
| 79          | mouse-5E7-a | CGGCTATG | CTTCGCCT | AATACG |
| 80          | mouse-5E7-b | TCCGCGAA | CTTCGCCT | AATACC |
| 81          | mouse-5E7-c | TCTCGCGC | CTTCGCCT | AATTAA |
| 82          | mouse-5E7-d | ATTACTCG | CTTCGCCT | AATTAT |
| 83          | mouse-5E7-e | TCCGGAGA | CTTCGCCT | AATTAG |
| 84          | mouse-5E7-f | CGCTCATT | CTTCGCCT | AATTAC |
| 85          | mouse-5E7-g | GAGATTCC | CTTCGCCT | AATTTA |
| 86          | mouse-5E7-h | AGCGATAG | CTTCGCCT | AATTTT |
| 87          | mouse-5E7-i | ATTACTCG | TAAGATTA | AATTTG |
| 88          | mouse-5E7-j | TCCGGAGA | TAAGATTA | AATTTC |
| ...         | ...         | ...      | ...      | ...    |
| 148         | A549-UN-c   | ATTCAGAA | AGGATAGG | AAGTAC |
| 149         | A549-T4MOI1-d | TAATGCGC | AGGATAGG | AAGTTA |
| 150         | A549-T4MOI5-d | CGGCTATG | AGGATAGG | AAGTTT |
| 151         | A549-T4MOI10-d | TCCGCGAA | AGGATAGG | AAGTTG |
| 152         | A549-T4MOI25-d | TCTCGCGC | AGGATAGG | AAGTTC |
| 153         | A549-PG13MOI1-d | AGCGATAG | AGGATAGG | AAGTGA |
| 154         | A549-PG13MOI5-d | ATTACTCG | TCAGAGCC | AAGTGT |
| 155         | A549-PG13MOI10-d | TCCGGAGA | TCAGAGCC | AAGTGG |
| 156         | A549-PG13MOI25-d | CGCTCATT | TCAGAGCC | AAGTGC |
| 157         | A549-UN-d | GAGATTCC | TCAGAGCC | AAGTCA |


#### ComparisonSheet.csv

This sheet is specific to Aerobio. It details the information describing a desired comparison to be made across _organism-condition_ biological replicates as described in the [sequencer sample / experiment replicate](#sequencer-samples-experiment-multiplexed-read-linkage) section of the [Exp-SampleSheet](#exp-samplesheetcsv).

The default name for this sheet is `ComparisonSheet.csv`.  This default name is used when looking for the sheet during a standard [phase 2](#)


### Experiment protocols


### Command configuration




# Experiment Types

## RNA-Seq

## dualRNA-Seq

## Tn-Seq

## WG-Seq

## Term-Seq

## scRNA-Seq

## scWG-Seq







![transformation DAG](../resources/public/images/xform-dag.png?raw=true)

As an example [Saite](https://github.com/jsa-aerial/saite) makes use of the framework aspects of Hanami and here is an example page layout from a session in it.



As described in the section on [sessions](#sessions) and session groups, the client may change the group its session belongs to (named by the `idefn` function of [start-server](#server-start)) as described in the section on [connections](#connection). This is achieved by sending the server a `:set-session-name` message, which has the form:


