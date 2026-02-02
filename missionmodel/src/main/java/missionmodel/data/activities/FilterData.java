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
            int volumeLeftToFilter = (int)(volume * percent);
            // assign it to the filtered bins, filling each one up as much as possible
            for(int i = 0; i < filteredBins.size() && volumeLeftToFilter > 0; ++i) {
                final var fbin = filteredBins.get(i);
                final var fbinVolume = Resources.currentValue(fbin.volume);
                final var fbinMaxVolume = Resources.currentValue(fbin.volume_ub);
                final int volumeLeftOnBin = (int) (fbinMaxVolume-fbinVolume);

                // skip this bin if it's full:
                if(volumeLeftOnBin == 0) continue;

                // else, fill up this bin as much as possible
                final var splitAmnt = Math.min(volumeLeftOnBin, volumeLeftToFilter);
                ModelActions.spawn(() -> fbin.receive(splitAmnt));

                // mark that that percent's been applied
                volumeLeftToFilter -= splitAmnt;
            }

            // empty the unfiltered bin
            ModelActions.spawn(() -> bin.remove(volume));
        }
    }
}
