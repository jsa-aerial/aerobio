
import os.path
import gzip
import argparse


parser = argparse.ArgumentParser()
parser.add_argument("-fqzin", action="store", dest="fqz")
parser.add_argument("-fqin", action="store", dest="fq")
parser.add_argument("-faot", action="store", dest="fa")
arguments = parser.parse_args()


def print_usage():
    print("\n" + "You are missing one or more required flags." +"\n\n")
    print("-fqin for uncompressed fastq input or -fqzin for compressed.")
    print("-faot for the collapsed fasta output file.")

if ((not arguments.fq and not arguments.fqz) or not arguments.fa):
    print_usage()
    quit()


def read_fqrec(infq):
    hd = infq.readline()
    if not hd:
        return []
    sq = infq.readline()
    aux = infq.readline()
    phred = infq.readline()
    return [hd,sq,aux,phred]


sqM = {}

if arguments.fq:
    infq = open(arguments.fq, "r")
else:
    infq = gzip.open(arguments.fqz, "rt")

with open(arguments.fa, 'w') as otfa:
    while True:
        fqrec = read_fqrec(infq)
        if fqrec == []:
            break
        sq = fqrec[1]
        if sq in sqM:
            sqM[sq] = sqM[sq] + 1
        else:
            sqM[sq] = 1
    infq.close()
    i = 1
    for sq,cnt in sqM.items():
        hd = ">" + str(i) + "-" + str(cnt) + "\n"
        otfa.write(hd)
        otfa.write(sq)
        i = i + 1



