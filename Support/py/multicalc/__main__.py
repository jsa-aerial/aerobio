# A translation of calc_fitness.pl into python! For analysis of Tn-Seq.
# This script requires BioPython, which in turn has a good number of dependencies (some optional but very helpful).
# How to install BioPython and a list of its dependencies can be found here: http://biopython.org/DIST/docs/install/Installation.html
# K. McCoy



# python ~/Bio/calc_fitness.py -ef .0 -el .10 -cutoff 0 -wig ./wiggle-file.wig -t1 /ExpOut/160804_NS500751_0017_AHF2KLBGXY/Out/Maps/19F-SDMMT2Vanc0.1.map -t2 /ExpOut/160804_NS500751_0017_AHF2KLBGXY/Out/Maps/19F-SDMMT2NoAb.map -ref /data1/NextSeq/Refs/NC_012469.gbk -out output.csv -expansion 300 -normalize foo


#(setq indent-tabs-mode t)
#(setq indent-tabs-mode nil)
#(setq tab-width 4)
#(setq python-indent-offset 4)


import re
import csv
import glob
import math
import argparse
import datetime
import os.path

from fitness import fitness
from normalize import normalize

##### ARGUMENTS #####

def print_usage():
    print "\n" + "You are missing one or more required flags. A complete list of flags accepted by calc_fitness is as follows:" + "\n\n"
    print "\033[1m" + "Required" + "\033[0m" + "\n"
    print "-ref" + "\t\t" + "The name of the reference genome file, in GTF/GFF format." + "\n"
    print "-features" + "\t" + "The feature types to use, defaults to 'CDS', can be comma separted string: 'gene,CDS' etc."
    print "-t1" + "\t\t" + "The name of the bowtie mapfile from time 1." + "\n"
    print "-t2" + "\t\t" + "The name of the bowtie mapfile from time 2." + "\n"
    print "-out" + "\t\t" + "Name of a file to enter the .csv output." + "\n"
    print "\n"
    print "\033[1m" + "Optional" + "\033[0m" + "\n"
    print "-expansion" + "\t" + "Expansion factor (default: 250)" + "\n"
    print "-d" + "\t\t" + "All reads being analyzed are downstream of the transposon" + "\n"
    print "-reads1" + "\t\t" + "The number of reads to be used to calculate the correction factor for time 0." + "\n\t\t" + "(default counted from bowtie output)" + "\n"
    print "-reads2" + "\t\t" + "The number of reads to be used to calculate the correction factor for time 6." + "\n\t\t" + "(default counted from bowtie output)" + "\n"
    print "-cutoff" + "\t\t" + "Discard any positions where the average of counted transcripts at time 0 and time 1 is below this number (default 0)" + "\n"
    print "-cutoff2" + "\t" + "Discard any positions within the normalization genes where the average of counted transcripts at time 0 and time 1 is below this number (default 0)" + "\n"
    print "-strand" + "\t\t" + "Use only the specified strand (+ or -) when counting transcripts (default: both)" + "\n"
    print "-reversed" + "\t" + "Experiment protocol used reversed i5 and i7 indices"
    print "-normalize" + "\t" + "A file that contains a list of genes that should have a fitness of 1" + "\n"
    print "-maxweight" + "\t" + "The maximum weight a transposon gene can have in normalization calculations" + "\n"
    print "-multiply" + "\t" + "Multiply all fitness scores by a certain value (e.g., the fitness of a knockout). You should normalize the data." + "\n"
    print "-ef" + "\t\t" + "Exclude insertions that occur in the first N amount (%) of gene--becuase may not affect gene function." + "\n"
    print "-el" + "\t\t" + "Exclude insertions in the last N amount (%) of the gene--considering truncation may not affect gene function." + "\n"
    print "-wig" + "\t\t" + "Create a wiggle file for viewing in a genome browser. Provide a filename." + "\n"
    print "-uncol" + "\t\t" + "Use if reads were uncollapsed when mapped." + "\n"
    print "\n"

parser = argparse.ArgumentParser()
parser.add_argument("-ref", action="store", dest="ref_genome")
parser.add_argument("-features", action="store", dest="features")
parser.add_argument("-t1", action="store", dest="mapfile1")
parser.add_argument("-t2", action="store", dest="mapfile2")
parser.add_argument("-out", action="store", dest="outfile")
parser.add_argument("-out2", action="store", dest="outfile2")
parser.add_argument("-expansion", action="store", dest="expansion_factor")
parser.add_argument("-d", action="store", dest="downstream")
parser.add_argument("-reads1", action="store", dest="reads1")
parser.add_argument("-reads2", action="store", dest="reads2")
parser.add_argument("-cutoff", action="store", dest="cutoff")
parser.add_argument("-cutoff2", action="store", dest="cutoff2")
parser.add_argument("-strand", action="store", dest="usestrand")
parser.add_argument("-reversed", action="store", dest="reversed")
parser.add_argument("-normalize", action="store", dest="normalize")
parser.add_argument("-maxweight", action="store", dest="max_weight")
parser.add_argument("-multiply", action="store", dest="multiply")
parser.add_argument("-ef", action="store", dest="exclude_first")
parser.add_argument("-el", action="store", dest="exclude_last")
parser.add_argument("-wig", action="store", dest="wig")
parser.add_argument("-uncol", action="store", dest="uncol")
arguments = parser.parse_args()

if (not arguments.ref_genome or not arguments.mapfile1 or not arguments.mapfile2 or not arguments.outfile):
    print_usage()
    quit()


# Default features to use
if (not arguments.features):
    arguments.features = 'CDS,gene'.split(',')
else:
    arguments.features = arguments.features.split(',')

# Sets the default value of the expansion factor to 250, which is a
# trivial placeholder number.

if (not arguments.expansion_factor):
    arguments.expansion_factor = 250

# 75 is similarly trivial

if (not arguments.max_weight):
    arguments.max_weight = 75

# Sets the default value of cutoff to 0; cutoff exists to discard
# positions with a low number of counted transcripts, because
# fitnesses calculated from them may not be very accurate, by the same
# reasoning that studies with low sample sizes are innacurate.

if (not arguments.cutoff):
    arguments.cutoff = 0

# Sets the default value of cutoff2 to 10; cutoff2 exists to discard
# positions within normalization genes with a low number of counted
# transcripts, because fitnesses calculated from them similarly may
# not be very accurate. This only has an effect if it's larger than
# cutoff, since the normalization step references a list of insertions
# already affected by cutoff.

if (not arguments.cutoff2):
    arguments.cutoff2 = 10

if (not arguments.usestrand):
    arguments.usestrand = "both"




##### PARSING THE REFERENCE GENOME #####

def get_time():
    return datetime.datetime.now().time()


def appendDictVal (d, k, v):
    if k in d:
        if 'feats' in d[k]:
            d[k]['feats'].append(v)
        else:
            d[k] = {'feats': [v]}
    else:
        d[k] = {'feats': [v]}


print "\n" + "Starting: " + str(get_time()) + "\n"

# Makes a dictionary out of each feature that's a gene - with its gene
# name, start location, end location, and strand as keys to their
# values. Then appends those dictionaries to the list associated with
# the refname for accessing later on.

def genome_length (refname):
    with open(glob.glob("/Refs/" + refname + "*.gbk")[0]) as fp:
        return float(fp.readline().split()[2])

main_strand = "+"
if (arguments.reversed):
    main_strand = "-"

featureset = dict(zip(arguments.features,arguments.features))
gtf_dict = {}

with open(arguments.ref_genome, 'r') as gtf:
    ##with open('NZ_CP1204_1205_522_523.gtf', 'r') as gtf:
    keys = ["kind", "start", "end", "strand", "gene"]
    for rec in csv.reader(gtf, delimiter='\t'):
        values = []
        if rec[2] in featureset:
            attrs = rec[8]
            loctag = re.sub('"', "", re.split(" +", re.split("; ", attrs)[0])[1])
            refname = rec[0]
            values.append(rec[2])
            start = float(rec[3])
            end = float(rec[4])
            strand = rec[6]

            # Exclude_first and exclude_last are used here to exclude
            # whatever percentage of the genes you like from
            # calculations; e.g. a value of 0.1 for exclude_last would
            # exclude the last 10% of all genes!  This can be useful
            # because insertions at the very start or end of genes
            # often don't actually break its function.
            if strand == "+":
                if (arguments.exclude_first):
                    start += (end - start) * float(arguments.exclude_first)
                if (arguments.exclude_last):
                    end -= (end - start) * float(arguments.exclude_last)
            else: # reverse strand - end with first and start with last
                if (arguments.exclude_first):
                    end -= (end - start) * float(arguments.exclude_first)
                if (arguments.exclude_last):
                    start += (end - start) * float(arguments.exclude_last)

            values.append(start)
            values.append(end)
            values.append(strand)
            values.append(loctag)
            d = dict(zip(keys, values))
            appendDictVal(gtf_dict, refname, d)
            if 'size' not in gtf_dict[refname]:
                gtf_dict[refname]['size'] = genome_length(refname)


outfile = arguments.outfile
outfile2 = arguments.outfile2
outwigfile = "/no/wigfile/wig.csv"
if (arguments.wig): outwigfile = arguments.wig

for k in gtf_dict:
    if len(gtf_dict) == 1:
        gtf_dict[k]['outfile'] = outfile
        gtf_dict[k]['outfile2'] = outfile2
        gtf_dict[k]['wigfile'] = outwigfile
    else:
        dnm = os.path.dirname(outfile)
        fnm = os.path.basename(outfile)
        fnm2 = os.path.basename(outfile2)
        wfnm = os.path.basename(outwigfile)
        gtf_dict[k]['outfile'] = os.path.join(dnm, k + '-' + fnm)
        gtf_dict[k]['outfile2'] = os.path.join(dnm, k + '-' + fnm2)
        gtf_dict[k]['wigfile'] = os.path.join(dnm, k + '-' + wfnm)


print "Done generating feature lookup: " + str(get_time()) + "\n"




##### PARSING THE MAPFILES #####

if (arguments.uncol):
    sys.exit("This script must use collapsed map input!!")


# When called, goes through each line of the mapfile to find the
# strand (+/Watson or -/Crick), count, and position of the read. It
# may be helpful to look at how the mapfiles are formatted to
# understand how this code finds them.

def read_mapfile(reads, refname):
    plus_total = 0
    minus_total = 0
    plus_counts = {"total": 0, "sites": 0}
    minus_counts = {"total": 0, "sites": 0}
    for read in reads:
        if (arguments.uncol):
            sys.exit("Not supposed to happen!")

        elif (refname != read.split()[4]):
            continue

        else:
            count = float(read.split()[0])
            strand = read.split()[1]
            position = float(read.split()[2])

            # If for some reason you want to skip all reads from one
            # of the strands - for example, if you wanted to compare
            # the two strands - that's done here.

            if arguments.usestrand != "both" and strand != arguments.usestrand:
                continue

            # Makes dictionaries for the + & - strands, with each
            # insert position as a key and the number of insertions
            # there as its corresponding value.

            if (strand == main_strand):
                sequence_length = float(read.split()[3])

                if arguments.downstream:
                    position += 0
                # The -2 in "(sequence_length -2)" comes from a
                # fake "TA" in the read; see how the libraries are
                # constructed for further on this
                else:
                    position += (sequence_length - 2)

                plus_counts["total"] += count
                plus_counts["sites"] += 1
                if position in plus_counts:
                    plus_counts[position] += count
                else:
                    plus_counts[position] = count
            else:
                minus_counts["total"] += count
                minus_counts["sites"] += 1
                if position in minus_counts:
                    minus_counts[position] += count
                else:
                    minus_counts[position] = count
    print "Map Counts: " + str(len(plus_counts)) + " " + str(len(minus_counts))
    #print "Map elts: " + str(plus_counts.items()[1:10])
    #print "Map 147: "  + str(plus_counts.get(147, -1)) + " " + str(minus_counts.get(147, -1))
    return (plus_counts, minus_counts)



print "args.downstream : " + str(arguments.downstream)


with open(arguments.mapfile1) as file:
    r1 = file.readlines()
with open(arguments.mapfile2) as file:
    r2 = file.readlines()

# Calls read_mapfile(reads) to parse arguments.reads1 and
# arguments.reads2 (your reads from t1 and t2).

for refname, dict in gtf_dict.items():
    print "    >>>> ", refname

    (plus_ref_1, minus_ref_1) = read_mapfile(r1, refname)
    print "Read first file: " + str(get_time()) + "\n"

    (plus_ref_2, minus_ref_2) = read_mapfile(r2, refname)
    print "Read second file: " + str(get_time()) + "\n"

    dict['pmrefs'] = {'pr1': plus_ref_1, 'mr1': minus_ref_1,
                      'pr2': plus_ref_2, 'mr2': minus_ref_2}

    # If reads1 and reads2 weren't specified in the command line, sets
    # them as the total number of reads (found in read_mapfile())
    if not arguments.reads1:
        dict['reads1'] = plus_ref_1["total"] + minus_ref_1["total"]
    else:
        dict['reads1'] = arguments.reads1

    if not arguments.reads2:
        dict['reads2'] = plus_ref_2["total"] + minus_ref_2["total"]
    else:
        dict['reads2'] = arguments.reads2



# The lines below are just printed for reference. The number of sites
# is the length of a given dictionary of sites - 1 because its last
# key, "total", isn't actually a site.

print "Reads:" + "\n"
print "1: + " + str(plus_ref_1["total"]) + " - " + str(minus_ref_1["total"]) + "\n"
print "2: + " + str(plus_ref_2["total"]) + " - " + str(minus_ref_2["total"]) + "\n"
print "Sites:" + "\n"
print "1: + " + str(plus_ref_1["sites"]) + " - " + str(minus_ref_1["sites"]) + "\n"
print "2: + " + str(plus_ref_2["sites"]) + " - " + str(minus_ref_2["sites"]) + "\n"




wigp = False
if (arguments.wig): wigp = True

normgene_file = arguments.normalize

# If making a WIG file is requested in the arguments, starts a string
# to be added to and then written to the WIG file with a typical WIG
# file header.  The header is just in a typical WIG file format; if
# you'd like to look into this more UCSC has notes on formatting WIG
# files on their site.

for refname, dict in gtf_dict.items():
    reads1 = dict['reads1']
    reads2 = dict['reads2']
    total = float((reads1 + reads2)/2.0)

    if (total == 0):
        continue

    wigstring = "track type=wiggle_0 name=" + dict['wigfile'] + "\n" + "variableStep chrom=" + refname + "\n"

    print '********** Reference: ', refname
    results,genic,total_inserts = fitness(dict['size'], dict['feats'],
                                          reads1, reads2,
                                          dict['pmrefs'], arguments)

    print "Genic: " + str(genic) + "\n"
    print "Total: " + str(total_inserts) + "\n"

    if (arguments.normalize):
        results, wigstring = normalize(wigp, wigstring, results,
                                       normgene_file, dict['outfile2'],
                                       total, refname, arguments)
        if wigp:
            with open(dict['wigfile'], "wb") as wigfile:
                wigfile.write(wigstring)

    elif wigp:
        for list in results:
            wigstring += str(list[0]) + " " + str(list[11]) + "\n"
        with open(dict['wigfile'], "wb") as wigfile:
            wigfile.write(wigstring)

    with open(dict['outfile'], "wb") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerows(results)




## cd multicalc/
## zip -r ../calc_fitness.zip *
## cd ..
## echo "#\!/usr/bin/env python" | cat - calc_fitness.zip > calc_fitness
