# Aerie Multi-Mission Models - Blackbird

This repository houses a collection of spacecraft subsystem models that can be configured, customized, and then run
within Aerie by a mission. By combining these models together with other models, such as the
[Aerie Simple Power Model](https://github.com/NASA-AMMOS/aerie-simple-model-power) and [Aerie Simple Data Model](https://github.com/NASA-AMMOS/aerie-simple-model-data),
a mission can build up an integrated spacecraft model quickly to perform mission trades and analyses.

The models in this
repository were originally derived from models created for the Blackbird planner, a java-based planning system developed
at NASA's Jet Propulsion Lab (JPL). There is also code included here for you to export and translate model data to the
free 3D visualization software [Cosmographia](https://cosmoguide.org/).

The following models are included in this repository:
- Geometry
- DSN (in work)
- Guidance Navigation and Control (in work)

Below you'll find short descriptions of each model and brief instructions on how to configure and run them. For general
instructions on how to compile models, see the instructions in the README of [mission model template repo](https://github.com/NASA-AMMOS/aerie-mission-model-template?tab=readme-ov-file#aerie-mission-model-template).
If you'd like to learn how to write Aerie models, please see our [modeling tutorial](https://nasa-ammos.github.io/aerie-docs/tutorials/mission-modeling/introduction/).

## Geometry Model

The geometry model provides an easy way for you to compute geometric quantities based on [SPICE kernel data](https://naif.jpl.nasa.gov/naif/data.html)
based on your spacecraft's trajectory. Example quantities that the model can compute include spacecraft to body distance and speed,
sub-spacecraft illumination angles, Sun-body-spacecraft angle, orbit period and inclination, body half-angle size, and more!
There are also some "spawner" activities within the model that will schedule activities to represent spacecraft eclipses,
occultations, and periapsis and apoapsis times.

Since the geometry model requires SPICE data to work, you'll need to include kernels for your spacecraft trajectory and
any other bodies to which you are computing relative geometry. There are some [example kernels](spice/kernels) included
in this repository for the Juno mission you can use to try out the model. When you are ready to use your own kernels,
you will want to update the [latest_meta_kernel.tm](spice/kernels/latest_meta_kernel.tm) file to point to your kernels.

Note: In order for Aerie to read SPICE files, you must mount a folder on your filesystem so that it’s shared with the
Docker containers in which Aerie runs. The [`docker-compose.yml`]() file in this repo already does this for you, so if you
start Aerie from that file and store your kernels in the [spice/kernels](spice/kernels) directory, you shouldn't have
to do anything special.

Bodies and geometric quantities you want to calculate are all configured in the [`default_geometry_config.json`](src/main/resources/missionmodel/default_geometry_config.json),
which gets bundled into the mission model jar when you compile the model.

Finally, in order to point the model to the right spacecraft to compute geometry against, you need to tell the model the
SPICE ID of that spacecraft. You can either do this via simulation configuration after you have uploaded the model or change
the default value in the [Configuration](src/main/java/missionmodel/Configuration.java) class.

## Acknowledgements

A special thanks to Chris Lawler and Flora Ridenhour, the original developers of the Blackbird planner, who have graciously provided the Blackbird multi-mission models to the Aerie team as a starting point for the models in this repository.

