"OriginalModel" uses the original filtering algorithm for FilterData.
This algorithm divides up the unfiltered data randomly between the filtered bins
(so, ie, in one run, 10% could go to bin0, in another 20%)

"NewAlgModel" changes the filtering algorithm for FilterData. 
The new algorithm tries to put all the unfiltered data into filtered bin 0. 
If bin 0 doesn't have enough space, then it is filled to capacity and the excess is put in bin 1 (til it's at capacity, and so on)

"10 Bin View" and "5 Bin View" refer to the number of buckets in the Data model.
I made this a configurable parameter defaulting to 10 for the sake of the demo.
If 10 isn't simulating fast enough, update the number of bins in the model's sim config to 5, 
then swap to the "5 Bin View" view file.