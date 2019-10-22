#!/usr/bin/perl -w
use strict;
use Getopt::Std;
use Bio::SeqIO;
use Set::Scalar;

sub print_usage() {

    print "\n";
    print "Usage: ./aggregate.pl -o outfile (options) file1 file2 file3...\n\n";

    print "Option List:\n\n";
    print " -o\tOutput file for aggregated data. (Required)\n";
    print " -c\tCheck for missing genes in the data set - provide a reference genome in\n";
    print " \tgenbank format. Missing genes will be sent to stdout.\n";
    print " -m\tPlace a mark in an extra column for this set of genes. Provide a file\n";
    print " \twith a list of genes seperated by newlines.\n";
    print " -x\tCutoff: Don't include fitness scores with average counts (c1+c2)/2 < x (default: 0)\n";
    print " -b\tBlanks: Exclude -b % of blank fitness scores (scores where c2 = 0) (default: 0 = 0%)\n";
    print " -w\tUse weighted algorithm to calculate averages, variance, sd, se\n";
    print " -l\tWeight ceiling: maximum value to use as a weight (default: 999,999)\n";
    print "\n";
}

# Get global options
our($opt_o, $opt_c, $opt_m, $opt_x, $opt_w, $opt_l, $opt_b);
getopt('ocmxwlb');
my $summary = $opt_o;
my $find_missing = $opt_c;
my $marked = $opt_m;
my $cutoff = $opt_x || 0;
my $blank_pc = $opt_b || 0;
my $weight_ceiling = $opt_l || 999999;
#print "o: $summary";
#print "$opt_o, $opt_c, $opt_m, $opt_x, $opt_w, $opt_l, $opt_b\n";

if (!$opt_o) { &print_usage; exit; }

# Returns mean, variance, sd, se
sub average {
    my $scoreref = shift @_;
    my @scores = @{$scoreref};

    my $sum=0;
    my $num=0;

    # Get the average.
    foreach my $w (@scores) {
        $sum += $w;
        $num++;
    }
    my $average= $sum/$num;
    my $xminusxbars = 0;

    # Get the variance.
    foreach my $w (@scores) {
        $xminusxbars += ($w-$average)**2;
    }
    my $variance = (1/($num-1)) * $xminusxbars;
    my $sd = sqrt($variance);
    my $se = $sd / sqrt($num);

    return ($average, $variance, $sd, $se);

}

# Takes two parameters, both hashrefs to lists.
# 1) hashref to list of scores
# 2) hashref to list of weights, equal in length to the scores.
sub weighted_average {

    my $scoreref = shift @_;
    my $weightref = shift @_;
    my @scores = @{$scoreref};
    my @weights = @{$weightref};

    my $sum=0;
    my ($weighted_average, $weighted_variance)=(0,0);
    my $v2;

    # Go through once to get total, calculate V2.
    for (my $i=0; $i<@weights; $i++) {
       $sum += $weights[$i];
       $v2 += $weights[$i]**2;
    }
    if ($sum == 0) { return 0; } # No scores given?

    my $scor = join (' ', @scores);
    my $wght = join (' ', @weights);
    #print "Scores: $scor\n";
    #print "Weights: $wght\n";

    # Now calculated weighted average.
    my ($top, $bottom) = (0,0);
    for (my $i=0; $i<@weights; $i++) {
        $top += $weights[$i] * $scores[$i];
        $bottom += $weights[$i];
    }
    $weighted_average = $top/$bottom;
    #print "WA: $weighted_average\n";

    ($top, $bottom) = (0,0);
    # Now calculate weighted sample variance.
    for (my $i=0; $i<@weights; $i++) {
       $top += ( $weights[$i] * ($scores[$i] - $weighted_average)**2);
       $bottom += $weights[$i];
    }
    $weighted_variance = $top/$bottom;
    #print "WV: $weighted_variance\n";

    my $weighted_stdev = sqrt($weighted_variance);
    my $weighted_stder = $weighted_stdev / sqrt(@scores);  # / length scores.

    #print "$weighted_average, $weighted_variance, $weighted_stdev\n";
    return ($weighted_average, $weighted_variance, $weighted_stdev, $weighted_stder);
}

my @marked;
my $marked_set = Set::Scalar->new;
if ($marked) {
   open MARKED, $marked;
   while (<MARKED>) {
      chomp $_;
      $marked_set->insert($_);
   }
   close MARKED;
   #print "Set members: ", $marked_set->members, "\n";
}

# Create Gene Summary File.
# Contains Gene => [w1,w2,w3,w4,etc.]
my %gene_summary;
foreach my $filename (@ARGV) {

   open IN, $filename;
   my $hdr = <IN>;
   my %hash;
   while (my $line = <IN>) {
      chomp $line;
      my @lines = split(/,/,$line);
      my $locus = $lines[9];
      my $w = $lines[12];
      if ($w and $w eq 'nW') {next;}
      if (!$w) { $w = 0 } # For blanks
      my $c1 = $lines[2];
      my $c2 = $lines[3];
      my $avg = ($c1+$c2)/2; # Later: change which function to use? C1? AVG(C1+C2)?
      if ($avg < $cutoff) { next; } # Skip cutoff genes.
      #if ($locus eq 'SP_0006') { print "$c1 < $cutoff\n";}
      if ($avg >= $weight_ceiling) { $avg = $weight_ceiling; } # Maximum weight.

      my @empty;
      if (!$gene_summary{$locus}) {
        $gene_summary{$locus}{w} = [@empty];
        $gene_summary{$locus}{s} = [@empty];
      }
      $gene_summary{$locus}{w} = [@{$gene_summary{$locus}{w}}, $w];  # List of Fitness scores.
      $gene_summary{$locus}{s} = [@{$gene_summary{$locus}{s}}, $avg]; # List of counts used to generate those fitness scores.

   }
   close IN;

}

####################################
# Print missing genes if we have to.
####################################
open SUMMARY, ">$summary";

if ($find_missing) {
   # print "In find_missing - reading gbk ... \n";
   # this variable should be set to the name of a genbank file.
   # print "Loading Genbank file.\n";
   # Pull in reference genome and feature list.
   my $in = Bio::SeqIO->new(-file=>$find_missing); #FindMissing is the genbank file.
   my $refseq = $in->next_seq;
   my @features = $refseq->get_SeqFeatures;

   my $feat_name;
   $feat_name = 'CDS';

   print SUMMARY "locus,mean,var,sd,se,gene,Total,Blank,Not Blank,Blank Removed,M\n";

   # Hashify features for easy lookup
   my @array_of_features;
   foreach my $feature (@features) {
      # print $feature->primary_tag, ($feature->primary_tag eq $feat_name), "\n";
      # Check if it's a gene.
      if ($feature->primary_tag eq $feat_name) {


        my @locus = $feature->get_tagset_values('locus_tag');
        my $locus = $locus[0] || '';

        my @gene = $feature->get_tagset_values($feat_name);
        my $gene = $gene[0] || '';

        my $sum=0;
        my $num=0;
        my $avgsum = 0;
        my $blank_ws = 0;

        # Count blanks.
        if ($gene_summary{$locus}) {
           # print "$locus, @{$gene_summary{$locus}{w}} \n";
           my $i=0;
           # Count blank fitness scores.
           foreach my $w (@{$gene_summary{$locus}{w}}) {
              if ($w == 0) {
                $blank_ws++;
              }
              else {
                 $sum += $w;
                 $num++;
              }
              $i++;
           }
            print "$locus, blank_ws : $blank_ws, i : $i, num : $num\n";
           my $count = $num + $blank_ws;

           # Remove blanks from scores if we need to.
           my $removed=0;
           my $to_remove = int($blank_pc * $count);
           if ($blank_ws > 0) {
              for (my $i=0; $i < @{$gene_summary{$locus}{w}}; $i++) {
                if ($removed == $to_remove) {last}
                my $w = ${$gene_summary{$locus}{w}}[$i];
                if ($w == 0) {
                    $removed++;
                    splice( @{$gene_summary{$locus}{w}}, $i, 1);
                    splice( @{$gene_summary{$locus}{s}}, $i, 1);
                    $i-=1;
                }
              }
           }

           # DEBUG
         #  if ($locus eq 'SP_0680') {
           #     print "$locus\t";
           #     foreach my $w (@{$gene_summary{$locus}{w}}) {
           #         print "$w\t"
            #    }print "\n";
            #    foreach my $s (@{$gene_summary{$locus}{s}}) {
            #        print "$s\t"
            #    }
           #     print "\n";
           #}#ENDDEBUG

           #if ($blank_ws > 0) { print "$gene\tInsertions:$count\tTo Remove:$to_remove\tTotal blank:$blank_ws\tRemoved:$removed\n"; }


            # Print out statistics.
            if ($num == 0 ) {
               print SUMMARY "$locus,0.10,0.10,X,X,$gene,$count,$blank_ws,$num,$removed";
            }
            else {
               # Get regular and weighted averages and associated stats.
               my ($average, $variance, $stdev, $stderr);
               # Use / Don't use weighted
               if (!$opt_w) { ($average, $variance, $stdev, $stderr) = &average($gene_summary{$locus}{w}) }
               else {  ($average, $variance, $stdev, $stderr)= &weighted_average($gene_summary{$locus}{w},$gene_summary{$locus}{s}); }
               print SUMMARY "$locus,$average,$variance,$stdev,$stderr,$gene,$count,$blank_ws,$num,$removed";
            }

        }
        else { # Not found.
           print SUMMARY "$locus,0.10,0.10,X,X,$gene,,,";
        }

         # Mark if marked.
         if ($marked && $marked_set->contains($locus)) {
               print SUMMARY ",M";
         }

         print SUMMARY "\n";

      }
   }

}
else {
   print SUMMARY "Locus,W,Count,SD,SE,M\n";
   # Now print out summary stats.
   foreach my $key (sort keys %gene_summary) {
        print "Key: $key\n";
        if (!$key) {next}
       my $sum=0;
       my $num=0;
       my $avgsum = 0;

       # Get the average.
       foreach my $w (@{$gene_summary{$key}{w}}) {
           $sum += $w;
           $num++;
       }
       my $average= $sum/$num;
       my $xminusxbars = 0;

       # Get the standard deviation.
       foreach my $w (@{$gene_summary{$key}{w}}) {
           $xminusxbars += ($w-$average)**2;
       }
       my ($sd, $se) = ('','');
       if ($num > 1) {
           $sd = sqrt($xminusxbars/($num-1));
           $se = $sd / sqrt($num);
       }

       print SUMMARY "$key,$average,$num,$sd,$se,";

       if ($marked && $marked_set->contains($key)) {
         print SUMMARY "M";
       }

       print SUMMARY "\n";
   }
}

close SUMMARY;
