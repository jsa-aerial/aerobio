## Alexander Farrell, Defne Surujon

## This program takes an input GBK file and writes a list of noralization genes
## Normalization genes for TnSeq are those that have the product annotation
## "Transposase" or "Mobile element"



import os
import csv 
from optparse import OptionParser

options = OptionParser(usage='%prog -i input',
                       description="Specify input gbk file and output file")

options.add_option("-i","--infile",dest="inputfile",
                   help="Input file (.gbk)")


options.add_option("--old",dest="old_tags",
					action="store_true", default=False,
					help="specify if old locus tags should be retrieved.")  
					
                   
# Read gbk file and return a dictionary of locus tag, product entries                   
def readgbkprod(filename,get_old_tags):
	if get_old_tags==True:
		loc_tag_flag="/old_locus_tag"
	else:
		loc_tag_flag= "/locus_tag"
	f=open(filename)
	lines=f.readlines()
	f.close()
	tags={}
	i=0
	prod=""
	SP=""
	count=0
	for line in lines:
		if i==1 and line[21]!= '/':
			if "repeat_region" in line or "     gene" in line:
				prodnew = prod.replace("\"","")
				prodfin = prodnew.replace("\n","")
				count+=1
				tags[SP] = prodfin
				i=0
			else:
				prod = prod + line[20:]
		if i==1 and '/' in line:
			prodnew = prod.replace("\"","")
			prodfin = prodnew.replace("\n","")
			count+=1
			tags[SP] = prodfin
			i=0
		if loc_tag_flag in line:
			SP = line.split('"')[-2]
		if "/product" in line:
			prod = line[31:]
			i=1
	print ("There are " + str(count) + " genes total")
	return tags


def makefasta(strain,prot,tags,otherfilename):
	writer = open(otherfilename,"w")

	for key, value in tags.items():
		fastaheader=">tvo|"+strain+".[gene="+key+"] [protein="+value+"]\n"
		writer.write(fastaheader)
		if key in prot:
			writer.write(prot[key]+"\n")
		else:
			writer.write("X\n")
			
	writer.close()

def main():
        opts, args = options.parse_args()
        strainname=opts.inputfile.split("/")[-1]
        strainname=strainname.split(".")[0]
        locus = readgbkprod(opts.inputfile,opts.old_tags)
        j=0
        g=open(strainname+'.txt','w')
        norm_gene_tags=["transposase","mobile element"]
        for locustag in locus:
                locus_description=locus[locustag].lower()
                if norm_gene_tags[0] in locus_description or norm_gene_tags[1] in locus_description:
                        g.write(locustag+"\n")
                        j+=1
        g.close()
        print("There are ",str(j)," normalization genes")
if __name__ == '__main__':
    main()
		
