package gov.nasa.jpl.mro.schedulers;

import gov.nasa.jpl.aerie.merlin.driver.ActivityDirective;
import gov.nasa.jpl.aerie.merlin.driver.ActivityDirectiveId;
import gov.nasa.jpl.aerie.merlin.driver.SimulationResults;
import gov.nasa.jpl.aerie.merlin.driver.json.SerializedValueJsonParser;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.scheduling.procedural.SchedulingProcedure;
import gov.nasa.jpl.aerie.timeline.Interval;
import gov.nasa.jpl.aerie.timeline.payloads.activities.AnyDirective;
import gov.nasa.jpl.aerie.timeline.payloads.activities.Directive;
import missionmodel.generated.telecom.DownlinkMapper;
import missionmodel.telecom.Downlink;
import org.apache.commons.lang3.mutable.MutableObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.MICROSECONDS;
import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.ZERO;
import static gov.nasa.jpl.mro.schedulers.Utils.*;

class SchedulerMain {
    public static String planId = "2";

    public static void main(String[] args) throws Exception {
        var plan = newPlan(
                Instant.parse("2033-09-17T00:00:00Z"),
                Instant.parse("2033-09-18T00:00:00Z"));

        resimulate(plan);
        runScheduler(new ScheduleDownlinks(), plan);
        resimulate(plan);

        System.out.println("=== Plan ===");

        serializePlan(plan);
    }

    private static PlanImpl newPlan(Instant planStart, Instant planEnd) {
        PlanImpl plan = new PlanImpl(
                planStart,
                new Interval($(ZERO), $(durationBetweenInstants(planStart, planEnd)), Interval.Inclusivity.Inclusive, Interval.Inclusivity.Inclusive),
                new MutableObject<>(null),
                new ArrayList<>()
        );
        return plan;
    }

    private static void serializePlan(PlanImpl plan) {
        final var directives = plan.directives();
        directives.sort(Comparator.comparingLong($ -> $($.getStartTime()).in(MICROSECONDS)));
        for (final var directive : directives) {

            System.out.println("{\"start_offset\": \"" + directive.getStartTime().toISO8601() + "\", \"type\": \"" + directive.getType() + "\", \"arguments\": " + argumentsToJson(directive.getInner().getArguments()) + ", \"plan_id\": " + planId + "},");
//            System.out.println(instantPlusDuration(plan.startTime(), $(directive.getStartTime())) + ": " + directive.getType() + directive.component1().component1());
        }
    }

    static String argumentsToJson(Map<String, SerializedValue> arguments) {
        return new SerializedValueJsonParser().unparse(SerializedValue.of(arguments)).toString();
    }

    private static void printPlan(PlanImpl plan) {
        final var directives = plan.directives();
        directives.sort(Comparator.comparingLong($ -> $($.getStartTime()).in(MICROSECONDS)));
        for (final var directive : directives) {
            System.out.println(instantPlusDuration(plan.startTime(), $(directive.getStartTime())) + ": " + directive.getType() + directive.component1().component1());
        }
    }

    private static void resimulate(PlanImpl planImpl) {
        System.out.println("Resimulating...");
        final var schedule = new LinkedHashMap<ActivityDirectiveId, ActivityDirective>();
        var count = 0L;
        for (final var directive : planImpl.directives()) {
            schedule.put(
                    new ActivityDirectiveId(count++),
                    new ActivityDirective($(directive.getStartTime()), directive.getType(), directive.component1().component1(), null, true));
        }
        SimulationResults newResults = SimulationUtility.simulate(schedule, planImpl.toAbsolute(planImpl.bounds().getStart()), $(planImpl.bounds().getEnd()).minus($(planImpl.bounds().getStart())));
        planImpl.latestSimulationResults().setValue(newResults);
    }

    private static void runScheduler(SchedulingProcedure scheduler, PlanImpl plan) throws Exception {
        runScheduler(scheduler, plan, plan.startTime());
    }

    private static void runScheduler(SchedulingProcedure scheduler, PlanImpl plan, Instant scheduleStartTime) throws Exception {
        scheduler.procedure(plan.startTime(), instantPlusDuration(plan.startTime(), $(plan.bounds().getEnd())), plan, new SchedulingProcedure.PlanManipulator() {
            void checkStartTime(Instant startTime) {
                if (startTime.isBefore(plan.startTime())) throw new IllegalArgumentException("Cannot schedule activities outside the plan");
            }
            @Override
            public void addActivity(Instant startTime, Downlink activity) {
                checkStartTime(startTime);
                plan.directives().add(new Directive<>(new AnyDirective(new DownlinkMapper().new InputMapper().getArguments(activity)), "Downlink", 0L, "Downlink", $(durationBetweenInstants(plan.startTime(), startTime))));
            }
        });
    }
}
