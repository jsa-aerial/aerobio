# Aerobio

An extensible full DAG streaming computation server with services and
jobs for RNA-Seq, Tn-Seq, WG-Seq and Term-Seq.

<a href="https://jsa-aerial.github.io/aerial.aerobio/index.html"><img src="https://github.com/jsa-aerial/aerobio/blob/master/resources/public/images/aero-blue.jpg" align="left" hspace="10" vspace="6" alt="aerobio logo" width="150px"></a>


**Aerobio** is a system for creating _services_ and connecting them together to form _jobs_.  A _service_ defines a piece of computation and may be _implemented_ by external tooling or code specific to the computation involved.  A _job_ defines a directed acyclical graph (DAG) composed of service nodes and data stream edges. Nodes may have multiple inputs and multiple outputs. Both services and jobs are specified entirely by data (EDN or JSON). Jobs are _realized_ by instantiating the node processing as (OS level) threads of execution and the streaming connections as aysnchronous channels.

While the system is general enough to apply to a wide range of domains, the intended target is specifically oriented toward hight throughput sequencing (HTS) analysis of RNA-Seq, Tn-SEq, WG-Seq, and Term-Seq.

