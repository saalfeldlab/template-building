#!/usr/bin/awk -E
NR==1{
    max=$1;
    sum=$1;
    n=0;
    next;
}
{
    sum+=$1;
    n++;
    if($1 > max) max=$1;
}

END{ if(n > 0) print "mean: " sum / n; print "max:  " max; }
