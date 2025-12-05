
import gzip
import argparse


parser = argparse.ArgumentParser()
parser.add_argument("-mapin", action="store", dest="mapin")
parser.add_argument("-mapot", action="store", dest="mapot")
arguments = parser.parse_args()


def print_usage():
    print("\n" + "You are missing one or more required flags." +"\n\n")
    print("-mapin for bowtie1 output map file.")
    print("-mapot for output map file used as input to fitness.")

if (not arguments.mapin or not arguments.mapot):
    print_usage()
    quit()


with open(arguments.mapin, "r") as inmap:
    with open(arguments.mapot, "w") as otmap:
        for l in inmap:
            rec = l.split("\t")
            lout = "\t".join([rec[0].split("-")[1], # read count
                              rec[1],               # strand
                              rec[3],               # start
                              str(len(rec[4])),     # read length
                              rec[2]])              # refname
            otmap.write(lout + "\n")
