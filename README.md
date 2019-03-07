# Aerobio

An extensible full DAG streaming computation server with services and
jobs for RNA-Seq, Tn-Seq, WG-Seq and Term-Seq.

<a href="https://jsa-aerial.github.io/aerial.aerobio/index.html"><img src="https://github.com/jsa-aerial/aerobio/blob/master/resources/public/images/aero-blue.png" align="left" hspace="10" vspace="6" alt="aerobio logo" width="150px"></a>


**Aerobio** is a system for creating _services_ and connecting them together to form _jobs_.  A _service_ defines a piece of computation and may be _implemented_ by external tooling or code specific to the computation involved.  A _job_ defines a directed acyclical graph (DAG) composed of service nodes and data stream edges. Nodes may have multiple inputs and multiple outputs. Both services and jobs are specified entirely by data (EDN or JSON). Jobs are _realized_ by instantiating the node processing as (OS level) threads of execution and the streaming connections as aysnchronous channels. A realized job is roughly equivalent to the notion of a 'pipeline'.

While the system is general enough to apply to a range of domains, the intended target is specifically oriented toward high throughput sequencing (HTS) analysis of RNA-Seq, Tn-SEq, WG-Seq, and Term-Seq data sets. This is realized by supporting the specification of experiment data processing by sets of spreadsheets. These spreadsheets are simple in structure and use the terminology of biologists. They are used to automatically construct jobs that will perform all necessary analysis for all samples and all comparisons. This includes:

* Conversion of sequencer output to fastq data sets

* Quality filtering of the data sets

* Splitting of the data sets according to samples

* Alignment of all sample reads creating sorted and indexed BAM and MAP files

* Gene counts for all samples creating tables for futher processing

* Differential Gene Expression analysis for RNA-Seq

* Fitness and aggregation analysis for Tn-Seq

* SNP analysis for WG-Seq

* Plots and charts for all analyses

All output is placed in configurable output locations according canonical and consistent naming conventions. Further, all data is also directly accessible via supplied web interfaces.
