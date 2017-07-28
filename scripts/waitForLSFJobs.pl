#!/usr/bin/perl -w

use strict;

$SIG{INT}=\&myCleanup;

# Usage: waitForLSFJobs.pl <verbose [2 or 1 or 0]> <delay in seconds in range 10-3600> [job IDs]
#
#
# Takes as args a string of bsub job IDs and periodically monitors them. Once they all finish, it returns 0
#
# If any of the jobs go into error state, an error is printed to stderr and the program waits for the non-error
# jobs to finish, then returns 1
#

# Usual qstat format - check this at run time
# JOBID  USER  STAT  QUEUE  FROM_HOST  EXEC_HOST  JOB_NAME  SUBMIT_TIME


# First thing to do is parse our input

my ($verbose, $delay, @jobIDs) = @ARGV;

# Check for user stupidity
if ($delay < 10) {
    print STDERR "Sleep period is too short, will poll queue once every 10 seconds\n";
    $delay = 10;
}
elsif ($delay > 3600) {
    print STDERR "Sleep period is too long, will poll queue once every 60 minutes\n";
    $delay = 3600;
}

print "  Waiting for " . scalar(@jobIDs) . " jobs: @jobIDs\n";

my $user=`whoami`;

my $qstatOutput = `bjobs -u $user`;

if (!scalar(@jobIDs) || !$qstatOutput) {
    # Nothing to do
    exit 0;
}

my @qstatLines = split("\n", $qstatOutput);

my @header = split('\s+', trim($qstatLines[0]));

# Position in qstat output of tokens we want
my $jobID_Pos = -1;
my $statePos = -1;

foreach my $i (0..$#header) {
    if ($header[$i] eq "JOBID") {
	$jobID_Pos = $i;
    }
    elsif ($header[$i] eq "STAT") {
	$statePos = $i;
    }
}

print "  JOBID pos : $jobID_Pos\n";
print "  STATE pos : $statePos\n";


# If we can't parse the job IDs, something is very wrong
if ($jobID_Pos < 0 || $statePos < 0) {
    die "Cannot find JOBID and STAT field in bjobs output, cannot monitor jobs\n";
}


# Now check on all of our jobs
my $jobsIncomplete = 1;

# Set to 1 for any job in an error state
my $haveErrors = 0;

while ($jobsIncomplete) {

    # Jobs that are still showing up in qstat
    $jobsIncomplete = 0;

    foreach my $job (@jobIDs) {
	# iterate over all user jobs in the queue
      qstatLine: foreach my $line (@qstatLines) {

	  # trim string for trailing white space so that the tokens are in the correct sequence
	  # We are being paranoid by matching tokens to job-IDs this way. Less elegant than a
	  # match but also less chance of a false-positive match
	  my @tokens = split('\s+', trim($line));

	  if ( $tokens[$jobID_Pos] eq $job) {
	      # Check status
          if ($tokens[$statePos] =~ m/E/) {
              if ($verbose > 1) {
              print "    Job $job has error.\n";
              }

		    $haveErrors = 1;
	      }
	      else {
		  $jobsIncomplete = $jobsIncomplete + 1;
	      }
	      if ($verbose > 1) {
		  print "    Job $job is in state $tokens[$statePos]\n";
	      }
	  }

	  last qstatLine if ( $tokens[$jobID_Pos] eq $job );
      }

    }


    if ($jobsIncomplete) {
	if ($verbose > 0) {
	    my $timestamp = `date`;
	    chomp $timestamp;
	    print "  ($timestamp) Still waiting for $jobsIncomplete jobs\n\n";
	}

	# Use of backticks rather than system permits a ctrl+c to work
	`sleep $delay`;
	$qstatOutput = `bjobs -u $user`;
	@qstatLines = split("\n", $qstatOutput);
    }

}

if ($haveErrors) {
    print "  No more jobs to run - some jobs had errors\n\n";
    exit 1;
}
else {
    print "  No more jobs in queue\n\n";
    exit 0;
}


sub trim {

    my ($string) = @_;

    $string =~ s/^\s+//;
    $string =~ s/\s+$//;

    return $string;
}


sub myCleanup {

    print "   ***   CTRL-C pressed, deleting remaining jobs   ***\n\n";
 
    my $cmd= "bkill " . join(" ", @jobIDs);

    `$cmd`;

    exit(1);
}
