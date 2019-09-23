##### NORMALIZATION #####


def normalize (wigp, wigstring, results, normgene_file, outfile2, total, refname, arguments):

    # Takes normalization genes (which should all be predicted or known to
    # have fitness values of exactly 1.0, like transposons for example)
    # and uses them to normalize the fitnesses of all insertion locations
    with open(normgene_file) as file:
        transposon_genes = file.read().splitlines()
    print "Normalize genes loaded" + "\n"

    blank_ws = 0
    sum = 0
    count = 0
    weights = []
    scores = []
    for list in results:
        if list[9] != '' and list[9] in transposon_genes: # and list[11]:
            c1 = list[2]
            c2 = list[3]
            score = list[11]
            avg = (c1 + c2)/2

            # Skips over those insertion locations with too few
            # insertions - their fitness values are less accurate
            # because they're based on such small insertion numbers.

            if float(c1) >= float(arguments.cutoff2):

                # Sets a max weight, to prevent insertion location
                # scores with huge weights from unbalancing the
                # normalization.

                if (avg >= float(arguments.max_weight)):
                    avg = float(arguments.max_weight)

                # Tallies how many w values are 0 within the
                # blank_ws value; you might get many transposon
                # genes with a w value of 0 if a bottleneck
                # occurs, for example, which is especially common
                # with in vivo experiments. This is used later by
                # aggregate.py For example, when studying a nasal
                # infection in a mouse model, what bacteria
                # "sticks" and is able to survive and what
                # bacteria is swallowed and killed or otherwise
                # flushed out tends to be a matter of chance not
                # fitness; all mutants with an insertion in a
                # specific transposon gene could be flushed out by
                # chance!

                if score == 0:
                    blank_ws += 1

                sum += score
                count += 1
                weights.append(avg)
                scores.append(score)

                ##print str(list[9]) + " " + str(score) + " " + str(c1)

    # Counts and removes all "blank" fitness values of normalization
    # genes - those that = 0 - because they most likely don't really
    # have a fitness value of 0, and you just happened to not get any
    # reads from that location at t2.

    blank_count = 0
    original_count = len(scores)
    curr_count = original_count
    i = 0
    print 'original_count:', original_count, 'scores[]:', scores
    while i < curr_count:
        w_value = scores[i]
        if w_value == 0:
            blank_count += 1
            weights.pop(i)
            scores.pop(i)
            i -= 1
            curr_count = len(scores)
        i += 1

        
    # If no normalization genes can pass the cutoff, normalization
    # cannot occur, so just return the original results
    if len(scores) == 0:
        print 'WARNING: The normalization genes do not have enough reads to pass cutoff and/or cutoff2'
        print 'try lowering one or both of those arguments.'
        print 'Returning unnormalized results...'
        return (results, wigstring)

    
    pc_blank_normals = float(blank_count) / float(original_count)
    with open(outfile2, "w") as f:
        f.write("# blank out of " + str(original_count) + ": " + str(pc_blank_normals) + "\n")
        f.write("blanks: " + str(pc_blank_normals) + "\n" + "total: " + str(total) + "\n" + "refname: " + refname + "\n")

        for list in results:
            if list[9] != '' and list[9] in transposon_genes:
                c1 = list[2]
                if float(c1) >= float(arguments.cutoff2):
                    f.write(str(list[9]) + " " + str(list[11]) + " " + str(c1) + "\n")

        average = sum / count
        i = 0
        weighted_sum = 0
        weight_sum = 0
        while i < len(weights):
            weighted_sum += weights[i]*scores[i]
            weight_sum += weights[i]
            i += 1
            weighted_average = weighted_sum/weight_sum

        f.write("Normalization step:" + "\n")
        f.write("Regular average: " + str(average) + "\n")
        f.write("Weighted Average: " + str(weighted_average) + "\n")
        f.write("Total Insertions: " + str(count) + "\n")

        old_ws = 0
        new_ws = 0
        wcount = 0

        for list in results:
            if list[11] == 'W':
                continue
            new_w = float(list[11])/weighted_average

            # Sometimes you want to multiply all the fitness values by
            # a constant; this does that. For example you might
            # multiply all the values by a constant for a genetic
            # interaction screen - where Tn-Seq is performed as usual
            # except there's one background knockout all the mutants
            # share.

            if arguments.multiply:
                new_w *= float(arguments.multiply)

            if float(list[11]) > 0:
                old_ws += float(list[11])
                new_ws += new_w
                wcount += 1

            list[12] = new_w

            if wigp:
                wigstring += str(list[0]) + " " + str(new_w) + "\n"

        old_w_mean = old_ws / wcount
        new_w_mean = new_ws / wcount
        f.write("Old W Average: " + str(old_w_mean) + "\n")
        f.write("New W Average: " + str(new_w_mean) + "\n")

    return (results, wigstring)
