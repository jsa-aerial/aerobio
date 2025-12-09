##### FITNESS CALCULATIONS #####


# Calculates the correction factors for reads from t1 and t2; cfactor1
# and cfactor2 are the number of reads from t1 and t2 respectively
# divided by total, which is the average number of reads between the
# two. This is used later on to correct for pipetting errors, or any
# other error that would cause unequal amounts of DNA from t1 and t2
# to be sequenced so that an unequal amount of reads is produced

import math

def fitness (genomelength, feature_list, reads1, reads2, pmrefs, arguments):

    total = float((reads1 + reads2)/2.0)
    cfactor1 = reads1/total
    cfactor2 = reads2/total
    print ("Cfactor 1: " + str(cfactor1) + "\n")
    print ("Cfactor 2: " + str(cfactor2) + "\n")

    plus_ref_1 = pmrefs['pr1']
    minus_ref_1 = pmrefs['mr1']
    plus_ref_2 = pmrefs['pr2']
    minus_ref_2 = pmrefs['mr2']

    cols = ["position", "strand", "count_1", "count_2", "ratio",
            "mt_freq_t1", "mt_freq_t2", "pop_freq_t1", "pop_freq_t2",
            "gene", "D", "W", "nW"]
    results = [cols]
    genic = 0
    total_inserts = 0
    i = 0
    while i < float(genomelength):

        # At each possible location for an insertion in the genome, counts
        # the number of actual insertions at t1 and which strand(s) the
        # corresponding reads came from.

        c1 = 0
        if i in plus_ref_1:
            c1 = float(plus_ref_1[i])
            strand = "+/"
            if i in minus_ref_1:
                c1 += float(minus_ref_1[i])
                strand = "b/"
        elif i in minus_ref_1:
            c1 = float(minus_ref_1[i])
            strand = "-/"

        # If there were no insertions at a certain location at t1 just
        # continues to the next location; there can't be any comparison to
        # make between t1 and t2 if there are no t1 insertions!

        else:
            i += 1
            continue

        # At each location where there was an insertion at t1, counts the
        # number of insertions at t2 and which strand(s) the corresponding
        # reads came from.

        c2 = 0
        if i in plus_ref_2:
            c2 = float(plus_ref_2[i])
            if i in minus_ref_2:
                c2 += float(minus_ref_2[i])
                strand += "b"
            else:
                strand += "+"
        elif i in minus_ref_2:
            c2 = float(minus_ref_2[i])
            strand += "-"

        # Corrects with cfactor1 and cfactor2

        c1 /= cfactor1
        if c2 != 0:
            c2 /= cfactor2
            ratio = c2/c1
        else:
            c2 = 0
            ratio = 0

        # Passes by all insertions with a number of reads smaller than
        # the cutoff, as they may lead to inaccurate fitness
        # calculations.

        if (c1 + c2)/2 < float(arguments.cutoff):
            i+= 1
            continue

        # Calculates each insertion's frequency within the populations
        # at t1 and t2.

        mt_freq_t1 = c1/total
        mt_freq_t2 = c2/total
        pop_freq_t1 = 1 - mt_freq_t1
        pop_freq_t2 = 1 - mt_freq_t2

        # Calculates each insertion's fitness! This is from the
        # fitness equation log((frequency of mutation @ time 2 /
        # frequency of mutation @ time 1)*expansion
        # factor)/log((frequency of population without the mutation @
        # time 2 / frequency of population without the mutation @ time
        # 1)*expansion factor)

        w = 0
        if mt_freq_t2 != 0:
            try:
                top_w = math.log(mt_freq_t2*(float(arguments.expansion_factor)/mt_freq_t1))
                bot_w = math.log(pop_freq_t2*(float(arguments.expansion_factor)/pop_freq_t1))
                w = top_w/bot_w
            except:
                print ("!!!!", "mt_freq_t2:", mt_freq_t2, "mt_freq_t1:", mt_freq_t1)
                print ("    ", "pop_freq_t2", pop_freq_t2, "pop_freq_t1", pop_freq_t1)

        # Checks which gene locus the insertion falls within, and
        # records that.

        gene = ''
        for feature_dictionary in feature_list:
            if feature_dictionary["start"] <= i and i <= feature_dictionary["end"]:
                gene = "".join(feature_dictionary["gene"])
                genic += 1
                break
        total_inserts += 1

        # Writes all relevant information on each insertion and its
        # fitness to a cvs file: the location of the insertion, its
        # strand, c1, c2, etc. (the variable names are
        # self-explanatiory). w is written twice, because the second w
        # will be normalized if normalization is called for, thus
        # becoming nW.
        row = [i, strand, c1, c2, ratio,
               mt_freq_t1, mt_freq_t2, pop_freq_t1, pop_freq_t2,
               gene, arguments.expansion_factor, w, w]

        results.append(row)
        i += 1

    return (results,genic,total_inserts)

