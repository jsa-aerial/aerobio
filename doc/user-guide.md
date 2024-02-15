# Aerobio

<a href="https://jsa-aerial.github.io/aerobio/index.html"><img src="https://github.com/jsa-aerial/aerobio/blob/master/resources/public/images/aero-blue.png" align="left" hspace="10" vspace="6" alt="aerobio logo" width="150px"></a>



Table of Contents
=================

   * [Overview]()
   * [Features]()
   * [gcloud]()
      * [Installation]()
      * [VM login]()
      * [SSH tunnels]()
   * [Experiment Types]()
      * [RNA-Seq]()
      * [dualRNA-Seq]()
      * [Tn-Seq]()
      * [WG-Seq]()
      * [Term-Seq]()
      * [scRNA-Seq]()
      * [scWG-Seq]()
         * [jklfdsjakl]()
      * [Aerobio command]()

toc[gh-md-toc](https://github.com/ekalinin/github-markdown-toc)

# Overview

**Aerobio** is a system for creating _services_ and connecting them together to form _jobs_.  A _service_ defines a piece of computation and may be _implemented_ by external tooling or code specific to the computation involved.  A _job_ defines a directed acyclical graph (DAG) composed of service nodes and data stream edges. Nodes may have multiple inputs and multiple outputs. Both services and jobs are specified entirely by data (EDN or JSON). Jobs are _realized_ by instantiating the node processing as (OS level) threads of execution and the streaming connections as aysnchronous channels. A realized job is roughly equivalent to the notion of a 'pipeline'.

While the system is general enough to apply to a range of domains, the intended target is specifically oriented toward high throughput sequencing (HTS) analysis of RNA-Seq, Tn-SEq, WG-Seq, and Term-Seq data sets. This is realized by supporting the specification of experiment data processing by sets of spreadsheets. These spreadsheets are simple in structure and use the terminology of biologists. They are used to automatically construct jobs that will perform all necessary analysis for all samples and all comparisons.


# Features

* jklfdsajklfdsa





# gcloud

## Installation

## VM login

## SSH tunnels



# Experiment Types

## RNA-Seq

## dualRNA-Seq

## Tn-Seq

## WG-Seq

## Term-Seq

## scRNA-Seq

## scWG-Seq

### jklfdsjakl


## Aerobio command

As noted, with respect to abstracting visualizations (ala' something like [Altair](https://altair-viz.github.io/)) there isn't much of an API and no classes, objects, or methods are involved. Most of what API there is centers on application development and start up. This is split across the server and the client side.
















![Hanami framework layout](resources/public/images/framework-page-structure.png?raw=true)

As an example [Saite](https://github.com/jsa-aerial/saite) makes use of the framework aspects of Hanami and here is an example page layout from a session in it.



As described in the section on [sessions](#sessions) and session groups, the client may change the group its session belongs to (named by the `idefn` function of [start-server](#server-start)) as described in the section on [connections](#connection). This is achieved by sending the server a `:set-session-name` message, which has the form:


