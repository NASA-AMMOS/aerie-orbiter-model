package missionmodel.data.activities;

import gov.nasa.jpl.aerie.contrib.streamline.core.Resources;
import gov.nasa.jpl.aerie.merlin.framework.ModelActions;
import gov.nasa.jpl.aerie.merlin.framework.annotations.ActivityType;
import gov.nasa.jpl.aerie.merlin.framework.annotations.Export;
import missionmodel.data.Data;
import missionmodel.data.DataMissionModel;

@ActivityType("FilterData")
public class FilterData {
    /**
     * The percent of data to be kept after filtering
     */
    @Export.Parameter
    public double percent = 0.75;

    @Export.Validation("Percent must be between 0 and 1, inclusive")
    @Export.Validation.Subject("percent")
    public boolean validatePercent() {
        return percent >= 0 && percent <= 1;
    }

    @ActivityType.EffectModel
    public void run(DataMissionModel model) {
        // Get Data model
        Data data = model.getData();
        var random = model.getRandom();
        var unfilteredBins = data.unfilteredOnboardBuckets;
        var filteredBins = data.filteredOnboardBuckets;

        // For each unfiltered bin:
        for(var bin : unfilteredBins) {
            // get the % of data we're filtering (dropping fractional bytes)
            final var volume = Resources.currentValue(bin.volume);
            final int filteredVolume = (int)(volume * percent);

            double percentLeftToFilter = 1;
            // assign it randomly to the filtered bins -- note that the last bin is excluded
            for(int i = 0; i < filteredBins.size()-1 && percentLeftToFilter > 0; ++i) {
                final var fbin = filteredBins.get(i);
                final var fbinVolume = Resources.currentValue(fbin.volume);
                final var fbinMaxVolume = Resources.currentValue(fbin.volume_ub);
                final var volumeLeftOnBin = (fbinMaxVolume-fbinVolume);

                // skip this bin if it's full:
                if(volumeLeftOnBin == 0) continue;

                // else, determine an appropriate amnt of data on this bin
                double splitPercent;
                int splitAmnt;
                do {
                    splitPercent = Math.floor(random.nextDouble(percentLeftToFilter) * 100) / 100;
                    splitAmnt = (int)(filteredVolume * splitPercent);
                } while (splitAmnt > volumeLeftOnBin);

                // add that data to the bin
                final var fSplitAmnt = splitAmnt;
                ModelActions.spawn(() -> fbin.receive(fSplitAmnt));

                // mark that that percent's been applied
                percentLeftToFilter -= splitPercent;
            }
            // last bin catches whatever still needs to be applied -- even if that's more than it has space for
            final var finalFBin = filteredBins.getLast();
            final var finalSplitAmnt = (int)(filteredVolume * percentLeftToFilter);
            ModelActions.spawn(() -> finalFBin.receive(finalSplitAmnt));

            // empty the unfiltered bin
            ModelActions.spawn(() -> bin.remove(volume));
        }
    }
}
