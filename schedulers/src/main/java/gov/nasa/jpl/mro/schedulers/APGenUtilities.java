package gov.nasa.jpl.mro.schedulers;

import missionmodel.Instants;
import spice.basic.CSPICE;
import spice.basic.SpiceErrorException;

import java.time.Instant;
import java.util.*;

public class APGenUtilities {
    static Map<String, String> NAIFBodyFrames = Map.of(
            "SUN", "iau_sun",
            "VENUS", "iau_venus",
            "EARTH", "iau_earth",
            "MOON", "iau_moon",
            "MARS", "iau_mars",
            "EUROPA", "iau_europa",
            "JUPITER", "iau_jupiter",
            "GANYMEDE", "iau_ganymede"
    );

    static ArrayList<Double> ComputeOccultationTimes(String obsrvr, String target, String OccultingBody, String OccultingBodyFrame, Instant IntervalStart, Instant IntervalEnd, double StepSize) throws SpiceErrorException {
        Objects.requireNonNull(obsrvr);
        Objects.requireNonNull(target);
        Objects.requireNonNull(OccultingBody);
        Objects.requireNonNull(OccultingBodyFrame);
        Objects.requireNonNull(IntervalStart);
        Objects.requireNonNull(IntervalEnd);
        return ComputeOccultationTimes("ANY", obsrvr, target, " ", "point", OccultingBody, OccultingBodyFrame, IntervalStart, IntervalEnd, StepSize, null, null);
    }

    static ArrayList<Double> ComputeOccultationTimes(String OccultationType, String obsrvr, String target, String TargetBodyFrame, String TargetShape, String OccultingBody, String OccultingBodyFrame, Instant IntervalStart, Instant IntervalEnd, double StepSize, List<Double> Occultation_Times, List<Integer> actualNumberOfWindows) throws SpiceErrorException {
        double et0, et1;
        String MGSO_time = "";
        double refval = 0.0;
        double adjust = 0.0;
        int i;
        int j;
        double start, stop;
        double dist;
        var pos = new double[3];
        double lt;
//
//  SPICEDOUBLE_CELL ( cnfine, MAXWIN );

        double[] cnfine = new double[0];
        double[] result = new double[3];
//  SPICEDOUBLE_CELL ( result, MAXWIN );
//  scard_c ( 0, &cnfine );
//  scard_c ( 0, &result );
//
//  strcpy( MGSO_time, SCET_PRINT_char_ptr( IntervalStart ));
        MGSO_time = Instants.formatToDOYStringWithoutZone(IntervalStart);
        et0 = CSPICE.str2et(MGSO_time);

        MGSO_time = Instants.formatToDOYStringWithoutZone(IntervalEnd);
        et1 = CSPICE.str2et(MGSO_time);

        cnfine = CSPICE.wninsd(et0, et1, cnfine);

        result = CSPICE.gfoclt(
                OccultationType,       //occtyp//
                OccultingBody,         //front//
                "ellipsoid",           //fshape//
                OccultingBodyFrame,    //fframe//
                target,                //back//
                TargetShape,           //bshape//
                TargetBodyFrame,       //bframe//
                "NONE",                  //abcorr//
                obsrvr,                //obsrvr//
                StepSize,              //step//
                9000, //nintvls,
                cnfine);               //cnfine//
        final var res = new ArrayList<Double>();
        for (final var x : result) {
            res.add(x);
        }
        return res;
    }
}
