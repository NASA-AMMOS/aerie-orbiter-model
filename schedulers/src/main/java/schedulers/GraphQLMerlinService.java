package schedulers;

//import gov.nasa.jpl.aerie.constraints.model.DiscreteProfile;
//import gov.nasa.jpl.aerie.constraints.model.LinearProfile;
import gov.nasa.jpl.aerie.json.BasicParsers;
import gov.nasa.jpl.aerie.merlin.driver.ActivityDirective;
import gov.nasa.jpl.aerie.merlin.driver.ActivityDirectiveId;
import gov.nasa.jpl.aerie.merlin.driver.SerializedActivity;
import gov.nasa.jpl.aerie.merlin.driver.engine.ProfileSegment;
import gov.nasa.jpl.aerie.merlin.protocol.model.SchedulerModel;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.DurationType;
//import gov.nasa.jpl.aerie.scheduler.model.Plan;
//import gov.nasa.jpl.aerie.scheduler.model.PlanningHorizon;
//import gov.nasa.jpl.aerie.scheduler.model.Problem;
//import gov.nasa.jpl.aerie.scheduler.model.SchedulingActivityDirective;
//import gov.nasa.jpl.aerie.scheduler.model.SchedulingActivityDirectiveId;
//import gov.nasa.jpl.aerie.scheduler.server.exceptions.NoSuchMissionModelException;
//import gov.nasa.jpl.aerie.scheduler.server.exceptions.NoSuchPlanException;
//import gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers;
//import gov.nasa.jpl.aerie.scheduler.server.graphql.ProfileParsers;
//import gov.nasa.jpl.aerie.scheduler.server.http.EventGraphFlattener;
//import gov.nasa.jpl.aerie.scheduler.server.http.InvalidEntityException;
//import gov.nasa.jpl.aerie.scheduler.server.http.InvalidJsonException;
//import gov.nasa.jpl.aerie.scheduler.server.models.ActivityAttributesRecord;
//import gov.nasa.jpl.aerie.scheduler.server.models.ActivityType;
//import gov.nasa.jpl.aerie.scheduler.server.models.DatasetId;
//import gov.nasa.jpl.aerie.scheduler.server.models.ExternalProfiles;
//import gov.nasa.jpl.aerie.scheduler.server.models.GoalId;
//import gov.nasa.jpl.aerie.scheduler.server.models.MerlinPlan;
//import gov.nasa.jpl.aerie.scheduler.server.models.MissionModelId;
//import gov.nasa.jpl.aerie.scheduler.server.models.PlanId;
//import gov.nasa.jpl.aerie.scheduler.server.models.PlanMetadata;
//import gov.nasa.jpl.aerie.scheduler.server.models.ProfileSet;
//import gov.nasa.jpl.aerie.scheduler.server.models.ResourceType;
//import gov.nasa.jpl.aerie.scheduler.server.models.UnwrappedProfileSet;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.timeline.payloads.activities.AnyDirective;
import gov.nasa.jpl.aerie.timeline.payloads.activities.Directive;

import javax.json.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import static gov.nasa.jpl.aerie.merlin.driver.json.SerializedValueJsonParser.serializedValueP;
import static schedulers.GraphQLParsers.durationFromPGInterval;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.activityAttributesP;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.discreteProfileTypeP;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.durationFromPGInterval;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.graphQLIntervalFromDuration;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.instantFromStart;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.parseGraphQLTimestamp;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.realDynamicsP;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.realProfileTypeP;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.simulationArgumentsP;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.ProfileParsers.discreteValueSchemaTypeP;
//import static gov.nasa.jpl.aerie.scheduler.server.graphql.ProfileParsers.realValueSchemaTypeP;

public record GraphQLMerlinService(URI merlinGraphqlURI, String hasuraGraphQlAdminSecret) {
    private static final java.time.Duration httpTimeout = java.time.Duration.ofSeconds(60);

    public MerlinPlan getPlanActivityDirectives(final int planId) {
        final var merlinPlan = new MerlinPlan();
        final var request = "query { plan_by_pk(id:%d) { activity_directives { id start_offset type arguments anchor_id anchored_to_start } duration start_time }} ".formatted(planId);
        final var response = postRequest(request);
        final var jsonplan = response.getJsonObject("data").getJsonObject("plan_by_pk");
        final var activityDirectives = jsonplan.getJsonArray("activity_directives");
        for (int i = 0; i < activityDirectives.size(); i++) {
            final var jsonActivity = activityDirectives.getJsonObject(i);
            final var type = activityDirectives.getJsonObject(i).getString("type");
            final var start = jsonActivity.getString("start_offset");
            final Integer anchorId = jsonActivity.isNull("anchor_id") ? null : jsonActivity.getInt("anchor_id");
            final boolean anchoredToStart = jsonActivity.getBoolean("anchored_to_start");
            final var arguments = jsonActivity.getJsonObject("arguments");
            final var deserializedArguments = BasicParsers
                    .mapP(serializedValueP)
                    .parse(arguments)
                    .getSuccessOrThrow((reason) -> new RuntimeException(List.of(reason).toString()));
            final var merlinActivity = new ActivityDirective(
                    durationFromPGInterval(start),
                    type,
                    deserializedArguments,
                    (anchorId != null) ? new ActivityDirectiveId(anchorId) : null,
                    anchoredToStart);
            final var actPK = new ActivityDirectiveId(jsonActivity.getJsonNumber("id").longValue());
            merlinPlan.addActivity(actPK, merlinActivity);
        }
        return merlinPlan;
    }

    public JsonObject postRequest(final String query) {
        return postRequest(query, Map.of());
    }

    public JsonObject postRequest(final String query, final Map<String, JsonValue> variables) {
        try {
            final var variablesJson = Json.createObjectBuilder();
            for (final var variable : variables.entrySet()) {
                variablesJson.add(variable.getKey(), variable.getValue());
            }
            final var reqBody = Json
                    .createObjectBuilder()
                    .add("query", query)
                    .add("variables", variablesJson)
                    .build();
            final var httpReq = HttpRequest
                    .newBuilder().uri(merlinGraphqlURI).timeout(httpTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Origin", merlinGraphqlURI.toString())
                    .header("x-hasura-admin-secret", hasuraGraphQlAdminSecret)
                    .POST(HttpRequest.BodyPublishers.ofString(reqBody.toString()))
                    .build();
            final var httpResp = HttpClient
                    .newHttpClient().send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
            if (httpResp.statusCode() != 200) {
                throw new RuntimeException();
            }
            final var respBody = Json.createReader(httpResp.body()).readObject();
            if (respBody.containsKey("errors")) {
                throw new RuntimeException(respBody.toString());
            }
            return respBody;
        } catch (final IOException | InterruptedException | JsonException e) { // or also JsonParsingException
            throw new RuntimeException("json parse error on graphql response:" + e.getMessage(), e);
        }
    }

    public void createActivityDirectives(
            final int planId,
            final List<Directive<AnyDirective>> newDirectives
    ) {
        final var query = """
        mutation createAllPlanActivityDirectives($activities: [activity_directive_insert_input!]!) {
          insert_activity_directive(objects: $activities) {
            returning {
              id
            }
            affected_rows
          }
        }
        """;

        final var insertionObjects = Json.createArrayBuilder();
        for (final var act : newDirectives) {
            var insertionObject = Json
                    .createObjectBuilder()
                    .add("plan_id", planId)
                    .add("type", act.getType())
                    .add("start_offset", act.getStartTime().toISO8601());


            var arguments = Json.createObjectBuilder();
            for (final var arg : act.inner.arguments.entrySet()) {
                arguments.add(arg.getKey(), serializedValueP.unparse(arg.getValue()));
            }
            insertionObject.add("arguments", arguments);
            insertionObjects.add(insertionObject.build());
        }

        final var arguments = Json
                .createObjectBuilder()
                .add("activities", insertionObjects.build())
                .build();

        final var response = postRequest(query, arguments);
    }

//    public void updatePlanActivityDirectives(int planId, List<Directive<AnyDirective>> directives) {
//        final var ids = new HashMap<SchedulingActivityDirective, ActivityDirectiveId>();
//        //creation are done in batch as that's what the scheduler does the most
//        final var toAdd = new ArrayList<SchedulingActivityDirective>();
//        for (final var activity : plan.getActivities()) {
//            if(activity.getParentActivity().isPresent()) continue; // Skip generated activities
//            final var idActFromInitialPlan = idsFromInitialPlan.get(activity.getId());
//            if (idActFromInitialPlan != null) {
//                //add duration to parameters if controllable
//                if (activity.getType().getDurationType() instanceof DurationType.Controllable durationType){
//                    if (!activity.arguments().containsKey(durationType.parameterName())){
//                        activity.addArgument(durationType.parameterName(), schedulerModel.serializeDuration(activity.duration()));
//                    }
//                }
//                final var actFromInitialPlan = initialPlan.getActivityById(idActFromInitialPlan);
//                //if act was present in initial plan
//                final var activityDirectiveFromSchedulingDirective = new ActivityDirective(
//                        activity.startOffset(),
//                        activity.type().getName(),
//                        activity.arguments(),
//                        (activity.anchorId() != null ? new ActivityDirectiveId(-activity.anchorId().id()) : null),
//                        activity.anchoredToStart()
//                );
//                final var activityDirectiveId = idsFromInitialPlan.get(activity.getId());
//                if (!activityDirectiveFromSchedulingDirective.equals(actFromInitialPlan.get())) {
//                    throw new MerlinServiceException("The scheduler should not be updating activity instances");
//                    //updateActivityDirective(planId, schedulerActIntoMerlinAct, activityDirectiveId, activityToGoalId.get(activity));
//                }
//                ids.put(activity, activityDirectiveId);
//            } else {
//                //act was not present in initial plan, create new activity
//                toAdd.add(activity);
//            }
//        }
//        final var actsFromNewPlan = plan.getActivitiesById();
//        for (final var idActInInitialPlan : idsFromInitialPlan.entrySet()) {
//            if (!actsFromNewPlan.containsKey(idActInInitialPlan.getKey())) {
//                throw new MerlinServiceException("The scheduler should not be deleting activity instances");
//                //deleteActivityDirective(idActInInitialPlan.getValue());
//            }
//        }
//
//        //Create
//        ids.putAll(createActivityDirectives(planId, toAdd, activityToGoalId, schedulerModel));
//
//        return ids;
//    }

//    public Map<SchedulingActivityDirective, ActivityDirectiveId> createActivityDirectives(
//            final PlanId planId,
//            final List<SchedulingActivityDirective> orderedActivities,
//            final Map<SchedulingActivityDirective, GoalId> activityToGoalId,
//            final SchedulerModel schedulerModel
//    )
//            throws IOException, NoSuchPlanException, MerlinServiceException
//    {
//        ensurePlanExists(planId);
//        final var query = """
//        mutation createAllPlanActivityDirectives($activities: [activity_directive_insert_input!]!) {
//          insert_activity_directive(objects: $activities) {
//            returning {
//              id
//            }
//            affected_rows
//          }
//        }
//        """;
//
//        //assemble the entire mutation request body
//        //TODO: (optimization) could use a lazy evaluating stream of strings to avoid large set of strings in memory
//        //TODO: (defensive) should sanitize all strings uses as keys/values to avoid injection attacks
//
//        final var insertionObjects = Json.createArrayBuilder();
//        for (final var act : orderedActivities) {
//            var insertionObject = Json
//                    .createObjectBuilder()
//                    .add("plan_id", planId.id())
//                    .add("type", act.getType().getName())
//                    .add("start_offset", act.startOffset().toString());
//
//            //add duration to parameters if controllable
//            final var insertionObjectArguments = Json.createObjectBuilder();
//            if(act.getType().getDurationType() instanceof DurationType.Controllable durationType){
//                if(!act.arguments().containsKey(durationType.parameterName())){
//                    insertionObjectArguments.add(durationType.parameterName(), serializedValueP.unparse(schedulerModel.serializeDuration(act.duration())));
//                }
//            }
//
//            final var goalId = activityToGoalId.get(act);
//            if (goalId != null) {
//                insertionObject.add("source_scheduling_goal_id", goalId.id());
//            }
//
//            for (final var arg : act.arguments().entrySet()) {
//                insertionObjectArguments.add(arg.getKey(), serializedValueP.unparse(arg.getValue()));
//            }
//            insertionObject.add("arguments", insertionObjectArguments.build());
//            insertionObjects.add(insertionObject.build());
//        }
//
//        final var arguments = Json
//                .createObjectBuilder()
//                .add("activities", insertionObjects.build())
//                .build();
//
//        final var response = postRequest(query, arguments).orElseThrow(() -> new NoSuchPlanException(planId));
//
//        final Map<SchedulingActivityDirective, ActivityDirectiveId> instanceToInstanceId = new HashMap<>();
//        try {
//            final var numCreated = response
//                    .getJsonObject("data").getJsonObject("insert_activity_directive").getJsonNumber("affected_rows").longValueExact();
//            if (numCreated != orderedActivities.size()) {
//                throw new NoSuchPlanException(planId);
//            }
//            var ids = response
//                    .getJsonObject("data").getJsonObject("insert_activity_directive").getJsonArray("returning");
//            //make sure we associate the right id with the right activity
//            for(int i = 0; i < ids.size(); i++) {
//                instanceToInstanceId.put(orderedActivities.get(i), new ActivityDirectiveId(ids.getJsonObject(i).getInt("id")));
//            }
//        } catch (ClassCastException | ArithmeticException e) {
//            throw new NoSuchPlanException(planId);
//        }
//        return instanceToInstanceId;
//    }


//    public Optional<List<DatasetMetadata>> getExternalDatasets(final PlanId planId)
//            throws MerlinServiceException, IOException
//    {
//        final var datasets = new ArrayList<DatasetMetadata>();
//        final var request = """
//        query {
//          plan_dataset(where: {plan_id: {_eq: %d}, simulation_dataset_id: {_is_null: true}}, order_by: {dataset_id:asc}) {
//            dataset_id
//            offset_from_plan_start
//          }
//        }
//        """.formatted(planId.id());
//        final var response = postRequest(request).get();
//        final var data = response.getJsonObject("data").getJsonArray("plan_dataset");
//        if (data.size() == 0) {
//            return Optional.empty();
//        }
//        for(final var dataset:data){
//            final var datasetId = new DatasetId(dataset.asJsonObject().getInt("dataset_id"));
//            final var offsetFromPlanStart = durationFromPGInterval(dataset
//                    .asJsonObject()
//                    .getString("offset_from_plan_start"));
//            datasets.add(new DatasetMetadata(datasetId, offsetFromPlanStart));
//        }
//        return Optional.of(datasets);
//    }

//    @Override
//    public ExternalProfiles getExternalProfiles(final PlanId planId)
//            throws MerlinServiceException, IOException
//    {
//        final Map<String, LinearProfile> realProfiles = new HashMap<>();
//        final Map<String, DiscreteProfile> discreteProfiles = new HashMap<>();
//        final var resourceTypes = new ArrayList<ResourceType>();
//        final var datasetMetadatas = getExternalDatasets(planId);
//        if(datasetMetadatas.isPresent()) {
//            for(final var datasetMetadata: datasetMetadatas.get()) {
//                final var profiles = getProfilesWithSegments(datasetMetadata.datasetId());
//                profiles.realProfiles().forEach((name, profile) -> {
//                    realProfiles.put(name,
//                            LinearProfile.fromExternalProfile(
//                                    datasetMetadata.offsetFromPlanStart,
//                                    profile.getRight()));
//                });
//                profiles.discreteProfiles().forEach((name, profile) -> {
//                    discreteProfiles.put(name,
//                            DiscreteProfile.fromExternalProfile(
//                                    datasetMetadata.offsetFromPlanStart,
//                                    profile.getRight()));
//                });
//                resourceTypes.addAll(extractResourceTypes(profiles));
//            }
//        }
//        return new ExternalProfiles(realProfiles, discreteProfiles, resourceTypes);
//    }

//    private Collection<ResourceType> extractResourceTypes(final ProfileSet profileSet){
//        final var resourceTypes = new ArrayList<ResourceType>();
//        profileSet.realProfiles().forEach((name, profile) -> {
//            resourceTypes.add(new ResourceType(name, profile.getLeft()));
//        });
//        profileSet.discreteProfiles().forEach((name, profile) -> {
//            resourceTypes.add(new ResourceType(name, profile.getLeft()));
//        });
//        return resourceTypes;
//    }


//    private UnwrappedProfileSet unwrapProfiles(final ProfileSet profileSet) throws MerlinServiceException {
//        return new UnwrappedProfileSet(unwrapProfiles(profileSet.realProfiles()), unwrapProfiles(profileSet.discreteProfiles()));
//    }

//    private <Dynamics> HashMap<String, Pair<ValueSchema, List<ProfileSegment<Dynamics>>>> unwrapProfiles(Map<String, Pair<ValueSchema,List<ProfileSegment<Optional<Dynamics>>>>> profiles)
//            throws MerlinServiceException
//    {
//        final var unwrapped = new HashMap<String,Pair<ValueSchema, List<ProfileSegment<Dynamics>>>>();
//        for(final var profile: profiles.entrySet()) {
//            final var unwrappedSegments = new ArrayList<ProfileSegment<Dynamics>>();
//            for (final var segment : profile.getValue().getRight()) {
//                if (segment.dynamics().isPresent()) {
//                    unwrappedSegments.add(new ProfileSegment<>(segment.extent(), segment.dynamics().get()));
//                }
//            }
//            unwrapped.put(profile.getKey(), Pair.of(profile.getValue().getLeft(), unwrappedSegments));
//        }
//        return unwrapped;
//    }

    private static <T> Duration sumDurations(final List<ProfileSegment<Optional<T>>> segments) {
        return segments.stream().reduce(
                Duration.ZERO,
                (acc, pair) -> acc.plus(pair.extent()),
                Duration::plus
        );
    }

//    private JsonValue buildAttributes(final Optional<Long> directiveId, final Map<String, SerializedValue> arguments, final Optional<SerializedValue> returnValue) {
//        return activityAttributesP.unparse(new ActivityAttributesRecord(directiveId, arguments, returnValue));
//    }

    /**
     * serialize the given string in a manner that can be used as a graphql argument value
     * @param s the string to serialize
     * @return a serialization of the object suitable for use as a graphql value
     */
    public String serializeForGql(final String s) {
        //TODO: can probably leverage some serializers from aerie
        //TODO: (defensive) should escape contents of bare strings, eg internal quotes
        //NB: Time::toString will format correctly as HH:MM:SS.sss, just need to quote it here
        return "\"" + s + "\"";
    }
}
