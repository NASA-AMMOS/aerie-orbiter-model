package schedulers;

import gov.nasa.jpl.aerie.merlin.driver.ActivityDirective;
import gov.nasa.jpl.aerie.merlin.driver.ActivityDirectiveId;
import gov.nasa.jpl.aerie.merlin.driver.SimulationResults;
import gov.nasa.jpl.aerie.merlin.driver.json.SerializedValueJsonParser;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.timeline.Duration;
import gov.nasa.jpl.aerie.timeline.Interval;
import gov.nasa.jpl.aerie.timeline.payloads.activities.AnyDirective;
import gov.nasa.jpl.aerie.timeline.payloads.activities.Directive;
import missionmodel.generated.telecom.DownlinkMapper;
import missionmodel.generated.visar.ChangeVisarDataModeMapper;
import missionmodel.generated.visar.VISAR_OffMapper;
import missionmodel.generated.visar.VISAR_OnMapper;
import missionmodel.telecom.Downlink;
import missionmodel.visar.ChangeVisarDataMode;
import missionmodel.visar.VISAR_Off;
import missionmodel.visar.VISAR_On;
import org.apache.commons.lang3.mutable.MutableObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.MICROSECONDS;
import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.ZERO;
import static schedulers.Utils.*;

class SchedulerMain {
    public static int planId = 1;

    public static void main(String[] args) throws Exception {
        var plan = getPlan(planId, Instant.parse("2033-09-17T00:00:00Z"), Instant.parse("2033-09-18T00:00:00Z"));

        resimulate(plan);
        runScheduler(new ScheduleDemObservations(), plan);
        runScheduler(new ScheduleDownlinks(), plan);

        commit(planId, plan);
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

    private static PlanImpl getPlan(int planId, Instant planStart, Instant planEnd) {
        var service = initializeMerlinService();
        var directives = service.getPlanActivityDirectives(planId);
        List<Directive<AnyDirective>> directivesList = new ArrayList<>();
        for (var directive : directives.getActivitiesById().values()) {
            directivesList.add(newDirective(directive.serializedActivity().getTypeName(), directive.serializedActivity().getArguments(), $(directive.startOffset())));
        }
        return new PlanImpl(
                planStart,
                new Interval($(ZERO),
                        $(durationBetweenInstants(planStart, planEnd)),
                        Interval.Inclusivity.Inclusive, Interval.Inclusivity.Inclusive),
                new MutableObject<>(null),
                directivesList);
    }

    static Directive<AnyDirective> newDirective(String type, Map<String, SerializedValue> arguments, Duration startTime) {
        return new Directive<>(new AnyDirective(arguments), type, 0L, type, startTime);
    }

    private static GraphQLMerlinService initializeMerlinService() {
        try {
            return new GraphQLMerlinService(new URI("http://localhost:8080/v1/graphql"), "aerie");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static void serializePlan(PlanImpl plan) {
        final var directives = plan.directives();
        directives.sort(Comparator.comparingLong($ -> $($.getStartTime()).in(MICROSECONDS)));
        for (final var directive : directives) {

            System.out.println("{\"start_offset\": \"" + directive.getStartTime().toISO8601() + "\", \"type\": \"" + directive.getType() + "\", \"arguments\": " + argumentsToJson(directive.getInner().getArguments()) + ", \"plan_id\": " + planId + "},");
//            System.out.println(instantPlusDuration(plan.startTime(), $(directive.getStartTime())) + ": " + directive.getType() + directive.component1().component1());
        }
    }

    private static void commit(int planId, PlanImpl plan) {
        var service = initializeMerlinService();
        service.createActivityDirectives(planId, plan.newDirectives());
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
                plan.addDirective(newDirective("Downlink", new DownlinkMapper().new InputMapper().getArguments(activity), $(durationBetweenInstants(plan.startTime(), startTime))));
            }

            @Override
            public void addActivity(Instant startTime, VISAR_On activity) {
              checkStartTime(startTime);
              plan.addDirective(newDirective("VISAR_On", new VISAR_OnMapper().new InputMapper().getArguments(activity), $(durationBetweenInstants(plan.startTime(), startTime))));
            }

            @Override
            public void addActivity(Instant startTime, VISAR_Off activity) {
              checkStartTime(startTime);
              plan.addDirective(newDirective("VISAR_Off", new VISAR_OffMapper().new InputMapper().getArguments(activity), $(durationBetweenInstants(plan.startTime(), startTime))));
            }

            @Override
            public void addActivity(Instant startTime, ChangeVisarDataMode activity) {
              checkStartTime(startTime);
              plan.addDirective(newDirective("ChangeVisarDataMode", new ChangeVisarDataModeMapper().new InputMapper().getArguments(activity), $(durationBetweenInstants(plan.startTime(), startTime))));
            }
        });
    }
}
