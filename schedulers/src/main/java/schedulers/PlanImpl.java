package schedulers;

import gov.nasa.jpl.aerie.merlin.driver.ActivityDirectiveId;
import gov.nasa.jpl.aerie.merlin.driver.SimulationResults;
import gov.nasa.jpl.aerie.merlin.driver.engine.ProfileSegment;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.timeline.Duration;
import gov.nasa.jpl.aerie.timeline.Interval;
import gov.nasa.jpl.aerie.timeline.collections.Directives;
import gov.nasa.jpl.aerie.timeline.collections.Instances;
import gov.nasa.jpl.aerie.timeline.ops.coalesce.CoalesceSegmentsOp;
import gov.nasa.jpl.aerie.timeline.payloads.Segment;
import gov.nasa.jpl.aerie.timeline.payloads.activities.AnyDirective;
import gov.nasa.jpl.aerie.timeline.payloads.activities.AnyInstance;
import gov.nasa.jpl.aerie.timeline.payloads.activities.Directive;
import gov.nasa.jpl.aerie.timeline.payloads.activities.Instance;
import gov.nasa.jpl.aerie.timeline.plan.Plan;
import kotlin.jvm.functions.Function1;
import org.apache.commons.lang3.mutable.Mutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.SECONDS;
import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.duration;
import static gov.nasa.jpl.aerie.timeline.Interval.Inclusivity.Exclusive;
import static gov.nasa.jpl.aerie.timeline.Interval.Inclusivity.Inclusive;
import static schedulers.Utils.*;

public final class PlanImpl implements Plan {
    private final Instant startTime;
    private final Interval bounds;
    private final Mutable<SimulationResults> latestSimulationResults;
    private final ArrayList<Directive<AnyDirective>> directives;
    private final ArrayList<Directive<AnyDirective>> newDirectives;

    public PlanImpl(
            Instant startTime,
            Interval bounds,
            Mutable<SimulationResults> latestSimulationResults,
            List<Directive<AnyDirective>> directives
    ) {
        this.startTime = startTime;
        this.bounds = bounds;
        this.latestSimulationResults = latestSimulationResults;
        this.directives = new ArrayList<>(directives);
        this.newDirectives = new ArrayList<>();
    }

    public void addDirective(Directive<AnyDirective> directive) {
        this.newDirectives.add(directive);
    }

    @Override
    public Interval totalBounds() {
        return bounds;
    }

    @Override
    public Interval simBounds() {
        return bounds;
    }

    @Override
    public Duration toRelative(Instant abs) {
        return $(durationBetweenInstants(startTime, abs));
    }

    @Override
    public Instant toAbsolute(Duration rel) {
        return instantPlusDuration(startTime, $(rel));
    }

    @Override
    public <V, TL extends CoalesceSegmentsOp<V, TL>> TL resource(String name, Function1<? super List<Segment<SerializedValue>>, ? extends TL> ctor) {
        final var result = new ArrayList<Segment<SerializedValue>>();
        final List<ProfileSegment<SerializedValue>> profile;
        if (latestSimulationResults.getValue().discreteProfiles.containsKey(name)) {
            profile = latestSimulationResults.getValue().discreteProfiles.get(name).getRight();
        } else if (latestSimulationResults.getValue().discreteProfiles.containsKey(name)) {
            profile = latestSimulationResults.getValue().realProfiles.get(name).getRight().stream().map($ -> new ProfileSegment<>($.extent(), SerializedValue.of(Map.of("initial", SerializedValue.of($.dynamics().initial), "rate", SerializedValue.of($.dynamics().rate))))).toList();
        } else {
            throw new IllegalArgumentException("Resource " + name + " not found in simulation results");
        }
        var offset = duration(0, SECONDS);
        for (final var segment : profile) {
            result.add(new Segment<>(new Interval($(offset), $(offset.plus(segment.extent())), Inclusive, Exclusive), segment.dynamics()));
            offset = offset.plus(segment.extent());
        }
        return ctor.invoke(result);
    }

    @Override
    public Instances<AnyInstance> allActivityInstances() {
        final var result = new ArrayList<Instance<AnyInstance>>();
        for (final var entry : latestSimulationResults.getValue().simulatedActivities.entrySet()) {
            final var simulatedActivity = entry.getValue();
            var activityStart = durationBetweenInstants(startTime, simulatedActivity.start());
            result.add(new Instance<>(
                    new AnyInstance(simulatedActivity.arguments(), simulatedActivity.computedAttributes()),
                    simulatedActivity.type(),
                    entry.getKey().id(),
                    entry.getValue().directiveId().map(ActivityDirectiveId::id).orElse(-1L),
                    new Interval($(activityStart), $(activityStart.plus(simulatedActivity.duration())), Inclusive, Inclusive)
            ));
        }
        for (final var entry : latestSimulationResults.getValue().unfinishedActivities.entrySet()) {
            final var unfinishedActivity = entry.getValue();
            var activityStart = durationBetweenInstants(startTime, unfinishedActivity.start());
            result.add(new Instance<>(
                    new AnyInstance(unfinishedActivity.arguments(), SerializedValue.of(Map.of())),
                    unfinishedActivity.type(),
                    entry.getKey().id(),
                    entry.getValue().directiveId().map(ActivityDirectiveId::id).orElse(-1L),
                    new Interval($(activityStart), bounds.getEnd(), Inclusive, Inclusive)
            ));
        }
        return new Instances<>(result);
    }

    @Override
    public Directives<AnyDirective> allActivityDirectives() {
        ArrayList<Directive<AnyDirective>> temp = new ArrayList<>(directives);
        temp.addAll(newDirectives);
        return new Directives<>(temp);
    }

    public Instant startTime() {
        return startTime;
    }

    public Interval bounds() {
        return bounds;
    }

    public Mutable<SimulationResults> latestSimulationResults() {
        return latestSimulationResults;
    }

    public List<Directive<AnyDirective>> directives() {
        var temp = new ArrayList<>(directives);
        temp.addAll(newDirectives);
        return temp;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PlanImpl) obj;
        return Objects.equals(this.startTime, that.startTime) &&
                Objects.equals(this.bounds, that.bounds) &&
                Objects.equals(this.latestSimulationResults, that.latestSimulationResults) &&
                Objects.equals(this.directives, that.directives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, bounds, latestSimulationResults, directives);
    }

    @Override
    public String toString() {
        return "PlanImpl[" +
                "startTime=" + startTime + ", " +
                "bounds=" + bounds + ", " +
                "latestSimulationResults=" + latestSimulationResults + ", " +
                "directives=" + directives + ']';
    }

    public List<Directive<AnyDirective>> newDirectives() {
        return new ArrayList<>(newDirectives);
    }
}
