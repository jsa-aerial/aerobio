
# Aerobio : HTS Analysis User Guide

<a href="https://jsa-aerial.github.io/aerobio/index.html"><img src="https://github.com/jsa-aerial/aerobio/blob/master/resources/public/images/aero-blue.png" align="left" hspace="10" vspace="6" alt="aerobio logo" width="150px"></a>

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
   * [Copying to and from the bucket when <em>on</em> the VM](#copying-to-and-from-the-bucket-when-on-the-vm)
* [Aerobio command](#aerobio-command)
   * [Commands](#commands)
   * [Parameters](#parameters)
   * [Analysis Phases](#analysis-phases)
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
         * [AggregationSheet.csv](#aggregationsheetcsv)
      * [Experiment protocols](#experiment-protocols)
      * [Command configuration](#command-configuration)
* [Experiment Output Structure](#experiment-output-structure)
* [Experiment Types](#experiment-types)
   * [RNA-Seq](#rna-seq)
      * [Analysis phases available](#analysis-phases-available)
   * [dualRNA-Seq](#dualrna-seq)
      * [Manual steps](#manual-steps)
      * [Analysis phases available](#analysis-phases-available-1)
   * [Tn-Seq](#tn-seq)
   * [WG-Seq](#wg-seq)
   * [Term-Seq](#term-seq)
   * [scRNA-Seq](#scrna-seq)
   * [scWG-Seq](#scwg-seq)
* [Special cases](#special-cases)
   * [Experiments spanning more than one sequencer run](#experiments-spanning-more-than-one-sequencer-run)
   * [Single sequencer run with multiple experiments](#single-sequencer-run-with-multiple-experiments)
   * [Comparisons across different experiments](#comparisons-across-different-experiments)

<!-- Created by https://github.com/ekalinin/github-markdown-toc -->

# Overview

**Aerobio** is a system for creating _services_ and connecting them together to form _jobs_.  A _service_ defines a piece of computation and may be _implemented_ by external tooling (commands like bowtie, gzip, etc) or functions (code specific to the computation involved). Services can also be _compound_ (a computation graph of other services).  A _job_ defines a program as a directed acyclical graph (DAG) composed of service nodes and data flow edges. Nodes may have multiple inputs and multiple outputs. Both services and jobs are specified entirely by data (EDN or JSON). Jobs are _realized_ by instantiating the node processing as (OS level) threads of execution and the data flow connections as streaming aysnchronous channels. A realized job is roughly equivalent to the notion of a 'pipeline'.

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

The experiment ID (EID) of an experiment is a unique identifier for that experiment.  This could be the name of the sequencer created directory when the input results are [direct from a sequencer](#direct-sequencer-output) or an informative name that will be used to create a directory that will hold the [experiment input](#experiment-input-structure).  The name should always start with the date of the sequencer run in `YYMMDD`




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

It is also possible to copy things to and from your local machine to the storage on the VM. If you do copy something _to_ the VM *never* copy to your home directory!  You should only copy to a directory on the [persistent disks](#persistent-disks). The most typical starting locations for this on these disks are `/Scratch`, `/ExpIn`, and `/ExpOut`.

**NOTE**: you must be on your *local* machine (laptop) when using these commands. *They do not work if you are on the VM!*

To copy, the gcloud command is again used as follows:

* Copying from your local machine to the VM:

```shell
# Copying to a location starting under /Scratch.
# /ExpIn and /ExpOut would look the same except for the 'Scratch'
gcloud compute scp <path-to-local-file> <your account>@aerobio-1:/Scratch/<path-to-VM-file>

# Directories can also be copied by using the recurse option
#
gcloud compute scp --recurse <path-to-local-dir> <your account>@aerobio-1:/Scratch/<path-to-VM-dir>
```

* Copying from VM to your local machine:

To copy a file or directory from the VM to your local machine, you use the same commands but put the path to the VM resource first and then your local path second:

```shell
gcloud compute scp  <your account>@aerobio-1:<path-to-VM-file> <path-to-local-file>

# And directories once again need the recurse option
#
gcloud compute scp --recurse <your account>@aerobio-1:<path-to-VM-dir> <path-to-local-location>
```

_NOTE_: you can copy any file/directory you have permissions to on the VM to your local machine; not just those on the [persistent disks](#persistent-disks).


## SSH tunnels

There are some services on the VM that are delivered by means of webserver based applications.  For these, you will be given a port on the VM where the server is listening (`vmport`) and a suggested _local_ port (`lclport`) to tunnel through to the VM port.  To activate this you again use the gcloud command with IAP tunneling.  Both the `vmport` and `lclport` will be _numbers_!  These names are used here simply as placeholders.

The command to use will be:


```shell
gcloud compute start-iap-tunnel aerobio-1 vmport --local-host-port=localhost:lclport --zone=us-central1-c &
```

This will start a daemon that runs in the background listening for any connection request.  Such a connection request would be done by opening a browser tab/window and putting `localhost:lclport` in as the web address to visit.


## Copying to and from the bucket when *on* the VM

You can also use the gcloud command to copy data to and from the [bucket](#gcp-storage-bucket) from or from an area on the [persistent disks](#persistent-disks).  For example a location under `/ExpIn` as part of [setting up](#experiment-input-structure) an experiment's home directory. The commands will make use of the `gisutil URL` of the bucket plus a subdirectory path to the data.  You can always get a copy of the full path to the data (including the gis bucket prefix) from the browser cloud console for the bucket.  The command will then be of the form:

* Copying from the bucket to the VM

```shell
# For files.  ExpIn is for illustration, other roots are possible
#
gcloud storage cp gs://<bucket-path-to-file> /ExpIn/<path-to-put-data>

# For directories. ExpIn is for illustration, other roots are possible
#
gcloud storage cp -r gs://<bucket-path-to-directory> /ExpIn/<path-to-put-data>

```

* Copying to the bucket from the VM

```shell
# For files.  ExpOut is for illustration, other roots are possible
#
gcloud storage cp /ExpOut/<path-to-file> gs://<bucket-path-to-file>

# For directories. ExpOut is for illustration, other roots are possible
#
gcloud storage cp -r  /ExpOut/<path-to-dir> gs://<bucket-path-to-directory>

```

* Copying analysis output to the bucket

For the specific case of copying [analysis output](#experiment-output-structure), to the bucket, there is a specific location and a general set of steps to use.  The location is the folder `ExpResults` in the top level of the bucket.  All analysis results (data under the `/ExpOut/<expid>/Out` directory) that you want in the bucket should be placed in this bucket folder.  The suggested best procedure for this is as follows:

1. First create a folder under `ExpResults` with the name ([expid](#experiment-id)) of the run. A good way to do this is to use the [bucket's console](https://console.cloud.google.com/storage/browser/aerobio1-expcache).  In the console, click on `ExpResults`. On the right the contents will show with an action bar above.  In this bar is `CREATE FOLDER`.  Click this, fill in the name as your `expid` and create.
2. Once created, go back to `ExpResults` and click on the new folder.
3. The path to the folder is just above its action bar and has a 'copy path to clipboard' icon on the right.  Click on this to get the path copied.
4. Now on the VM we can use one of the 'copy to bucket from VM' commands, where `<bucket-path-to-directory>` is the paste of the copied output path from 3.




# Aerobio command

Currently, the primary means of accessing the aerobio server for running [jobs](#job-phases) is the `aerobio` command line tool.  To use it you need a terminal shell session (CLI) by [logging into](#vm-login) a VM.  Once on a VM in a terminal session, the command will be directly available at the command line. Issuing the command with no parameters will issue a synopsis of its usage (short help).

In the following,

* Elements enclosed within angle brackets, `<` and `>`, and separated by bars, `|`, indicate one and only one must be given. Ex: `<a | b>` means exactly _one_ of `a` or `b` must be given.

* Elements enclosed within braces, `{` and `}` and separated by bars, `|`, indicate at most one can be given, but none may be given - they are optional. Ex: `{a | b}` means exactly one of `a` or `b` or nothing may be given.


The general format of the `aerobio` command for running the various supported HTS jobs is:

```shell
aerobio <cmd> <action | compfile | aggrfile> {replicates | combined} <eid>
```

## Commands

`cmd` is one of the following values

   * `run` : this is the primary command for Aerobio across all types of jobs, not just the supported HTS analysis jobs.  Here, `run` expects a standard set of jobs for the typical [phases](#analysis-phases) of analysis per experiment. `run` takes a phase designator and the [experiment's ID (EID)](#experiment-id). Example successful command:

     ```shell
     aerobio run phase-0 230627_01_dualRNASeq_KZ
     
     Job launch: Successful
     ```


   * `check` : This command is typically used before initiating any job runs.  It invokes the validator which performs integrity checks on the various aspects of the [experiment structure and definitions](#experiment-input-structure).  It reports  any errors found in detail and in a quite experimenter friendly format. `check` takes only an [experiment id (EID)](#experiment-id). Examples:

     - Forgot to move definition sheets to experiment directory

     ```shell
     aerobio check 240216_Rosch_BHN97

     Experiment '240216_Rosch_BHN97' -
      1. is missing required 'SampleSheet.csv'
      2. is missing required 'Exp-SampleSheet.csv'
      3. is missing required 'ComparisonSheet.csv'
     ```

     - Typo (`q` instead of `w`) and repid not digit or character

     ```shell
     aerobio check 230313_NS500751_0207_AHYGWMBGXH

     230313_NS500751_0207_AHYGWMBGXH/Exp-SampleSheet.csv has errors
      1. rep id `10` in `BT-wt-10` must be a single upper case or lower case letter or digit

     230313_NS500751_0207_AHYGWMBGXH/ComparisonSheet.csv has errors
      1. `BT-qt` is not a sample listed in Exp-SampleSheet
     ```


   * `compare` : This command is for subsequent comparisons beyond those specified in the default ComparisonSheet.  It takes a comparison sheet name and the [experiment id (EID)](#experiment-id).  The comparison sheet name will be for a comparison sheet in the [experiment directory](#experiment-input-structure).  It will typically contain new comparisons and will run just the comparisons.

     ```shell
     aerobio compare ComparisonSheet_pg13_only.csv 230627_01_dualRNASeq_KZ
     ```


   * `xcompare` : This command invokes the job for processing comparisons involving [data from more than one experiment](#comparisons-across-different-experiments).

   * `aggregate` : Invokes Tn-Seq global aggregation job.  Typically used for post aggregation of standard Tn-Seq output for in-vivo analysis using bottleneck numbers.  Takes a global (aka 'super') [aggregation CSV file](#aggregationsheetcsv) and the (EID)](#experiment-id)


## Parameters

* `action` : A designator for a job for a specific [analysis phase](#analysis-phases). This is a required parameter of the `run` command in the standard HTS processing.

* `compfile` : This parameter is for the `compare` command and is the name of a comparison sheet (including the `.csv` file type) that denotes a different set of comparisons from the default `ComparisonSheet.csv` for a given experiment (as denoted by its EID).

* `aggrfile` : Is the name of the file containing the specification of the [global aggregation](#aggregationsheetcsv) desired over a completed Tn-Seq run.

* `replicates` : The literal value "replicates".  If given, processing occurs at the [technical replicate](#sequencer-samples-experiment-multiplexed-read-linkage) level.  Each such replicate is processed separately. This is the *default* processing.  **NOTE** : mutually exclusive with `combined`.

* `combined` : The literal value "combined". If given the [technical replicates](#sequencer-samples-experiment-multiplexed-read-linkage) are processed as a combined whole. **NOTE** : mutually exclusive with `replicates`.

* `EID`: The [experiment's ID](#experiment-id)


## Analysis Phases

There are three main phases for the standard HTS processing jobs in Aerobio.  They are collectively known across [experiment types](#supported-experiment-types) as `phase-0`, `phase-1`, and phase-2`.  The basic processing of each phase is generally the same across experiment types, but there are differences, both subtle and large, for each type.  Details of the specific processing of each phase for each experiment type are given in [detail section](#experiment-types) of each experiment type.


* `phase-0` : This phasae can run conversion processing on [direct sequencer output](#direct-sequencer-output), sets up the experment's [canonical output structure](#experiment-output-structure), runs a quality control step across the input fastqs, and various types of demultiplexing and clustering of the input reads.

* `phase-1` : The major processing in phase-1 is alignment processing. There are various aligners that may be used and the main output are the resulting [BAM files](https://samtools.github.io/hts-specs/SAMv1.pdf).

* `phase-2` : Phase-2 processing encompasses the greatest variability across experimnet types.  This is due to each [experiment type](#supported-experiment-types) having a different focus of investigation. Therefore, the results processing for these will of necessity be different.  Nevertheless, the interface for the experimenter (user) running the analysis is the same, it will still be the `phase-2` job (action) to the [run command](#commands).  Details of the processing involved for each experiment type can be found in the specific type's [detail section](#experiment-types).



# Experiment Input Structure

The input for an experiment's automated processing starts with the creation of a directory under [ExpIn](#persistent-disks) which will function as the _experiment's home directory_.  This directory is also known as the EID (experiment identifier) of the experiment. The directory will contain the starting input data.  This will be either the direct sequencer output or a set of fastq files that have been "pre-converted" from a direct sequencer output.  Which of these two is available will depend on the deliverables of your sequencing facility.  This directory will also be where you put the [experiment specification](#experiment-specifications) files defining the experiment analysis.

## Direct Sequencer Output

If you have the direct sequencer output directory it can be the directory for the experiment under `/ExpIn`.  With this directory in the [bucket](#gcp-storage-bucket), you can copy the directory to `/ExpIn` using [gcloud cli](#copying-from-the-bucket).  For example, let's say your sequencer output directory in the bucket  is `240411_example_exp` and was generated by the TVOLab's AVITI sequencer.  Then you could perform the transfer with[^1]:

```shell
gcloud storage cp -r gs://aerobio1-expcache/ElemBio/AV242502/2400411_example_exp /mnt/disks/Data/ExpIn
```
[^1]: A bug in gcloud cli prevents this from being simply `/ExpIn`

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

These comprise three tabular specificatins, typically built as spreadsheets (in Excel or similar) and saved as CSV files in DOS or Linux standard line terminatioin format.  The key is to ensure lines have line feed characters (LF) as part of their termination.  Apple standard, Macbook et al., does **not** include an LF as a line terminator.  So, if you are working on Apple devices you need to ensure that you save your spreadsheet defined files in DOS or similar format.  If you do not do this, the Aerobio validator will flag your definition CSV sheets as having an erroneous format.

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

4. `Sample_ID` and `Sample_Name` **must not** have underscores `_` in them.  They can have dashes `-`.

5. 'index' is the I7 index for the sample

6. 'index2', if given, is the I5 index of the sample

7. The barcodes for I7 and I5 _must_ be composed of the uppercase letters 'A', 'T', 'G', 'C.


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

* _Experiment sub-reads_ : These reads constitute subsets within the sequencer samples. Generally, they are identified with a biological source, replicate within a source, organism, or some other experiment specific _attributes_. They are identified by barcodes attached to the relevant sequences.

* _Experiment barcodes_ : These are the barcodes attached to sequences to identify an experiment's various reads within the different sequencer samples. They pick out (or delineate) the various subsets of reads corresponding to sources, source replicates, species, etc.  They may or may not span samples.

Given the above, Aerobio needs a means by which it can pull out reads from samples that are specific to a particular experimental attribute and aggregate them into separate fastq files. That's what the read linkage section of the Exp-SampleSheet.csv is for.


1. This section specifies how experiment barcodes match up with each I7 index, or I7 and I5 index combination, that identifies a sequencer sample.  Additionally, it provides a short hand means of naming the resulting aggregated fastq files containing the reads with a given experiment barcode.

Format

There are two formats, depending on whether there is only an I7 index or an I7-I5 index combination for the sequencer sample


* I7 only

| n   | organism-biorep-techrep | I7 index | ExpBC |
| --- | ----------------------- | -------- | ----- |

* I7-I5 combination

| n   | organism-biorep-techrep | I7 index | I5 index | ExpBC |
| --- | ----------------------- | -------- | -------- | ----- |


2. _n_ is an integer, the ordinal number of the row in the section (1, 2, ...)


3. _organism-biorep-techrep_ provides three pieces of information.

   * The _organism_.  This _must_ be one of the lab IDs provided in the [genome lab id xref](#genome-official-name-lab-id-cross-reference).

   * The _biorep_.  This should be a short description of what the associated reads involve.  Some examples, "CIPT0" for ciprofloxacin at time 0 or "T4Broth" for tigr4 in broth media. Again, this text must *not* contain spaces or any punctuation. In the literature, these different 'samples' described are known as [biological replicates](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4825082/#:~:text=Broadly%20speaking%2C%20biological%20replicates%20are,the%20measuring%20equipment%20and%20protocols).

   * The _techrep_.  This is an indicator of the different measurements of the biological replicate.  In the literature these are known as [technical replicates](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4825082/#:~:text=Broadly%20speaking%2C%20biological%20replicates%20are,the%20measuring%20equipment%20and%20protocols). Currently these indicators _must_ be a single digit [0-9] or single lower case letter [a-z]. Using digits and letters is **mutually exclusive**.  This also means there is currently a limit of a **maximum of  26 replicates** per organism-condition.  These constraints may be lifted in the future.


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
| **n** | **org-biorep-techrep** | **i7**        | **ExpBC**  |
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
| **n** | **org-biorep-tecrep** | **i7**  | **I5**      | **ExpBC**  |
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

#### AggregationSheet.csv


### Experiment protocols


### Command configuration

For all the [tools](#overview) used in Aerobio services a set of defaults for their possible switch values is used when they are run as part of a [service](#overview) during a [job](#overview).  These values work well in most cases.  However, there are situations and experimental setup and conditions that may require adfjustments to the default switches and values and/or additional switches.  We can change or add switches and values for tools by means of the `cmd.config` file.

* Format

The format of the file is [EDN](https://github.com/edn-format/edn) which is a simpler yet  more capable data notation than [JSON](https://www.json.org/json-en.html). Here, this is restricted to simple maps (aka dictionaries) where a top level key is the name of a tool and its value is a map of its switches and their corresponding values.

*NOTE* in contrast to JSON, EDN does *not* have a colon (:) between key and value.  Also, commas are *optional* - you can use them or not.

Keys are quoted strings - characters enclosed between double quotes (").  The values of top level keys (tool names) are  again maps.  The keys of these maps are quoted strings, naming a switch of the command, and the value of a switch can be quoted strings, numbers, or keywords (a colon (:) followed by alphanumerics like `:na`).

```clojure
{"tool1name" {"switch1" "string value"
              "switch2" 1
              "switch-that-has-no-value" :na}
 "tool2name" {"switch" "some string value"}
 ...
 "toolNname" {...}
}
```

* Examples

  - Your experiment has paired end reads.  You need to tell `featureCounts` that this is the case by supplying the "-p" switch (which takes no value).  You would put the following into your `cmd.config` file:

  ```clojure
  {"featureCounts" {"-p" :na}}
  ```

  - Your experiment has mouse (eukaryote) reads and you are using the [STAR](https://github.com/alexdobin/STAR) aligner and want to decrease the minimum percent overlap (normalized to read length) of a match to genome from 0.66 to 0.33.  This will increase the amount of reads mapping to the genome.  Additionally, you have paired end reads and want to indicate stranded read countingb in `featureCounts`.  Your `cmd.config` would look like this:
  
  ```clojure
  {"STAR" {"--outFilterScoreMinOverLread"  "0.33"
           "--outFilterMatchNminOverLread"  "0.33"}
   "featureCounts" {"-p" :na
                    "-s" 1}
  }
  ```



# Experiment Output Structure

The output from an experiment's Aerobio processing is placed into a _canonical_ directory structure under [ExpOut](#(persistent-disks). The canonical aspect is important as _all_ output data for _all_ experiments of _all_ experiment types is uses the same naming conventions and locations.



# Experiment Types

This section provides details of the processing for each supported experiment type.  This includes the specific phase designators available for the [run command](#commands) of the experiment type, descriptions of the specifics of the processing done in each [analysis phase](#analysis-phases) as well as the various output artifacts produced in each phase.

## RNA-Seq

The [dispatch code](#experiment-type-information) in the [Exp-SampleSheet](#exp-samplesheetcsv) must be `rnaseq`.

### Analysis phases available

Phase-0 : `phase-0`, `phase-0b`, `phase-0c`, and `phase-0d`

* `phase-0`
    
1. runs conversion software (bcl2fastq or bcl-convert). This takes [direct sequencer output](#direct-sequencer-output) and produces the starting set of fastq files that will be the primary input for subsequent processing.

2. creates and initializes the [output structure](#experiment-output-structure) for the experiment.  Creates the descriptive database of the experiment.  Moves fastqs to their processing area in the output structure.

3. runs a streaming quality control (QC) filter on input fastqs

4. concurrently reads the streaming QC data, collecting and saving [experiment barcodes (ExpBCs)](#sequencer-samples-experiment-multiplexed-read-linkage) statistics.

5. concurrently reads the streaming QC data plus the ExpBCstatistics to demultiplex the various biorep-techrep reads into their own fastqs.

* `phase-0b`

  Excludes first step of running conversion software.  Runs 2-5 of `phase-0`.  Supports manual conversion or facility delivered [fastq files](#fastq-files)

* `phase-0c`

  Runs 3-5 of `phase-0`.  Assumes output structure, database, and fastqs are setup and ready to process.


Phase-1 : `phase-1`

1. runs alignment over the `biorep-techrep` output fastqs from `phase-0`.  Different aligners may be used including bowtie, bowtie2, STAR, bwa, et.al. The alignment data is streamed to samtools step 2

2. samtools conversion from aligner SAM (text) output to BAM (compressed binary) output.  This data is continually streamed to step 3 and step 5.

3. samtools sort of streaming BAM streamed to step 4

4. samtools indexing run on sorted BAM data creating canonically named and located BAI index files

5. Output the BAM data to canonically named and located BAM files (colocated with the BAI files)


Phase-2 : `phase-2`

1. Using directions in [comparison sheet](#comparisonsheetcsv) runs sets of differential gene expression analyses.  The default job/analysis uses a combination of `featureCounts` to create count matrices of reads per annotation and DESeq2 taking these count matrices and running a negative binomial model on them to get log <sub>2</sub> expression changes.

  * `featureCounts : reads the BAMs of phase-1 plus gff/gtf annotation data files for the genome(s) and counts the number of reads occuring in 'genes'.  'Genes' here are [CDS sequences](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC403693/#:~:text=An%20important%20step%20in%20the%20analysis%20of%20genome%20information%20is,ends%20with%20a%20stop%20codon.).  Outputs count matrices as CSV files.

  * `DESeq2` : 


## dualRNA-Seq

The [dispatch code](#experiment-type-information) in the [Exp-SampleSheet](#exp-samplesheetcsv) must be `dual-rnaseq`.

[dualRNA-Seq](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5313147/) involves the simultaneous RNA-Seq analysis of a bacterial pathogen and its infected host. It incorporates both eukaryote and bacterial reads per [sequencer output sample](#sequencer-samples-experiment-multiplexed-read-linkage) for the same condition aspects of the biological replicates involved.  This complicates processing in a number of ways that need to be accommodated.  Some of this is handled automatically by Aerobio, but currently there are a couple of things that need some manual intervention.

### Manual steps

* **No experiment barcodes:** Typically dualRNA-Seq reads do not have [experiment barcodes](#sequencer-samples-experiment-multiplexed-read-linkage) for separating a sample's [sub-reads](#sequencer-samples-experiment-multiplexed-read-linkage).  This is because dualRNA-Seq samples are separated by the host and bacteria organisms involved. To accommodate this, two things need to be done.
  1. First the [exp params](#experiment-type-information) field in the [Exp-SampleSheet header](#experiment-type-information) line needs to be set to the value `single-index` to indicate there are no actual barcodes on the reads.
  2. Second, a unique *artificial* barcode for each unique combination of i7 and i5 indices in the [sample linkage section](#experiment-type-information) of the Exp-SampleSheet.csv.
Both of these are shown in the dualRNA-Seq [example](#experiment-sheet-examples) for Exp-SampleSheets.

* **Paired end reads:** dualRNA-Seq typically has paired end reads. So, you need to account for this by telling `featureCounts` that the BAM files have paired end reads.  You need to use the [cmd.config](#command-configuration) file to do this and examples in that section specifically show this case.

* **Different aligners for organisms:** This impacts only phase-1 of the [analysis phases](#analysis-phases). Best practice indicates that eukaryote and prokaryote organisms should use different aligners.  To handle this you currently need to have different [Exp-SampleSheets](#exp-samplesheetcsv) for the organism sets involved.  Typically, this involves three such sheets: one for eukaryotes, one for prokaryotes, and one that has all organsims specified.  The latter is used for the phase-0 and phase-2 jobs of the analysis run.

  To make this work, you need to use a few Linux command line tools. In addition to the usual change directory `cd`, list directory `ls`, copy file `cp`, and remove file `rm`, you also need the link command `ln`.  The link command is used to make a "pointer file" (symbolic/soft link file) from the name `Exp-SampleSheet.csv` to one of the previously described three experiment sheets.  The suggsted steps to do this whole process are the following:

  1. Create three Exp-SampleSheets and them each a relevant name.  For example, for a run with PG13 and mouse data, `PG13-Exp-SampleSheet.csv`, `mouse-Exp-SampleSheet.csv` and `all-Exp-SampleSheet.csv` would be good names for the three different sheets.

  1. Use the `ln` command to link each of these **in turn** to the name `Exp-SampleSheet.csv`. While in the [experiment's home directory](#experiment-input-structure), this is done as in this example: `ln -s ./PG13-Exp-SampleSheet.csv ./Exp-SampleSheet.csv`.  You **must** use the `-s` switch for this!  **NOTE:** if `Exp-SampleSheet.csv` is currently pointing to something, you *must* first remove it with the `rm` command.

  1. With the `Exp-SampleSheet.csv` set to `all-Exp-SampleSheet.csv` run appropriate phase-0 (see below).

  1. Next set `Exp-SampleSheet.csv` to either the PG13 or mouse file. If you first set it to the PG13 file, you would then run `phase-1` (see below).  Next, you would `rm Exp-SampleSheet.csv` and then set it to `mouse-Exp-SampleSheet.csv`: `ln -s ./mouse-Exp-SampleSheet.csv ./Exp-SampleSheet.csv`.  You would then run `star-phase-1` (see below).

  1. At this point, you have BAM files for PG13 and mouse using the proper aligner for each.  Now you want to run `phase-2` analysis (DGE, fitness, etc).  So, you remove the current link for `Exp-SampleSheet.csv` and set it back to `all-Exp-SampleSheet.csv`.

  Here is a command line terminal session showing all this for the example of PG13 and mouse data.  This assumes you have made the three sheets and have already [copied them](#vm-scp) to the [experiment's home directory](#experiment-input-structure)

  ```shell
  # First, change directory to the experiment's home directory
  #
  $ cd /ExpIn/dualRNA-example

  # List current Exp sheets
  #
  $ ls -l *Exp*.csv
  all-Exp-SampleSheet.csv
  mouse-Exp-SampleSheet.csv
  PG13-Exp-SampleSheet.csv

  # For phase-0, link to all file
  #
  $ ln -s ./all-Exp-SampleSheet.csv ./Exp-SampleSheet.csv

  # list to see if we have what we want
  #
  $ ls -l *Exp*.csv
  all-Exp-SampleSheet.csv
  Exp-SampleSheet.csv -> all-Exp-SampleSheet.csv
  mouse-Exp-SampleSheet.csv
  PG13-Exp-SampleSheet.csv
  
  # Run phase-0
  $ aerobio run phase-0 dualRNA-example

  # Once phase-0 finishes, run bowtie2 on PG13 data
 
  # First, set the link to PG13
  #
  $ rm Exp-SampleSheet.csv
  $ ln -s ./PG13-Exp-SampleSheet.csv ./Exp-SampleSheet.csv
  $ ls -l *Exp*.csv
  all-Exp-SampleSheet.csv
  Exp-SampleSheet.csv -> PG13-Exp-SampleSheet.csv
  mouse-Exp-SampleSheet.csv
  PG13-Exp-SampleSheet.csv

  # Run phase-1 (bowtie2)
  $ aerobio run phase-1 dualRNA-example

  # Once phase-1 finishes, run STAR on mouse

  # First, set the link to mouse
  #
  $ rm Exp-SampleSheet.csv
  $ ln -s ./mouse-Exp-SampleSheet.csv ./Exp-SampleSheet.csv
  $ ls -l *Exp*.csv
  all-Exp-SampleSheet.csv
  Exp-SampleSheet.csv -> mouse-Exp-SampleSheet.csv
  mouse-Exp-SampleSheet.csv
  PG13-Exp-SampleSheet.csv

  # Run star-phase-1 (STAR aligner)
  $ aerobio run star-phase-1 dualRNA-example

  # Once star-phase-1 finishes, reset to all for phase 2

  # Reset the link to all file
  #
  $ rm Exp-SampleSheet.csv
  $ ln -s ./all-Exp-SampleSheet.csv ./Exp-SampleSheet.csv
  $ ls -l *Exp*.csv
  all-Exp-SampleSheet.csv
  Exp-SampleSheet.csv -> all-Exp-SampleSheet.csv
  mouse-Exp-SampleSheet.csv
  PG13-Exp-SampleSheet.csv

  # Run phase-2
  $ aerobio run phase-2 dualRNA-example

  ```



### Analysis phases available

Phase-0 : `phase-0`, `phase-0b`, `phase-0c`, and `phase-0d`

* `phase-0`
    
1. runs conversion software (bcl2fastq or bcl-convert). This takes [direct sequencer output](#direct-sequencer-output) and produces the starting set of fastq files that will be the primary input for subsequent processing.

2. creates and initializes the [output structure](#experiment-output-structure) for the experiment.  Creates the descriptive database of the experiment.  Moves fastqs to their processing area in the output structure.

3. runs a streaming quality control (QC) filter on input fastqs

4. concurrently reads the streaming QC data, collecting and saving [experiment barcodes (ExpBCs)](#sequencer-samples-experiment-multiplexed-read-linkage) statistics.

5. concurrently reads the streaming QC data plus the ExpBCstatistics to demultiplex the various biorep-techrep reads into their own fastqs.

* `phase-0b`

  Excludes first step of running conversion software.  Runs 2-5 of `phase-0`.  Supports manual conversion or facility delivered [fastq files](#fastq-files)

* `phase-0c`

  Runs 3-5 of `phase-0`.  Assumes output structure, database, and fastqs are setup and ready to process.


Phase-1 : `phase-1`, `star-phase-1`

1. runs alignment over the `biorep-techrep` output fastqs from `phase-0`.  Use `phase-1` to run [bowtie2](https://bowtie-bio.sourceforge.net/bowtie2/manual.shtml) aligner (prokaryote alignment). Use `star-phase-1` to run the [STAR](https://github.com/alexdobin/STAR) aligner (eukaryote alignment). The alignment data is streamed to samtools step 2

2. samtools conversion from aligner SAM (text) output to BAM (compressed binary) output.  This data is continually streamed to step 3 and step 5.

3. samtools sort of streaming BAM streamed to step 4

4. samtools indexing run on sorted BAM data creating canonically named and located BAI index files

5. Output the BAM data to canonically named and located BAM files (colocated with the BAI files)


Phase-2 : `phase-2`

1. Using directions in [comparison sheet](#comparisonsheetcsv) runs sets of differential gene expression analyses.  The default job/analysis uses a combination of `featureCounts` to create count matrices of reads per annotation and DESeq2 taking these count matrices and running a negative binomial model on them to get log <sub>2</sub> expression changes.

  * `featureCounts : reads the BAMs of phase-1 plus gff/gtf annotation data files for the genome(s) and counts the number of reads occuring in 'genes'.  'Genes' here are [CDS sequences](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC403693/#:~:text=An%20important%20step%20in%20the%20analysis%20of%20genome%20information%20is,ends%20with%20a%20stop%20codon.).  Outputs count matrices as CSV files.

  * `DESeq2` : 


## Tn-Seq

The [dispatch code](#experiment-type-information) in the [Exp-SampleSheet](#exp-samplesheetcsv) must be `tnseq`.


## WG-Seq

The [dispatch code](#experiment-type-information) in the [Exp-SampleSheet](#exp-samplesheetcsv) must be `wgseq`.


## Term-Seq

The [dispatch code](#experiment-type-information) in the [Exp-SampleSheet](#exp-samplesheetcsv) must be `termseq`.


## scRNA-Seq

The [dispatch code](#experiment-type-information) in the [Exp-SampleSheet](#exp-samplesheetcsv) must be `scrnaseq`.


## scWG-Seq

The [dispatch code](#experiment-type-information) in the [Exp-SampleSheet](#exp-samplesheetcsv) must be `scwgseq`.




# Special cases


## Experiments spanning more than one sequencer run


## Single sequencer run with multiple experiments


## Comparisons across different experiments



![transformation DAG](../resources/public/images/xform-dag.png?raw=true)








