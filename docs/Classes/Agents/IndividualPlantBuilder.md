# 42 words

An `IndividualPlantBuilder` is a [PlantBuildingManager](./PlantBuildingManager.md) that creates [Portfolios](../Modules/Portfolio.md) from a predefined list of power plants.

# Details

The `IndividualPlantBuilder` uses a one-time provided list of power plants to match the current power plant `Portfolio`.
On every update of its Portfolio, the IndividualPlantBuilder removes outdated  [PowerPlants](../Modules/PowerPlant.md) from the according to the current simulation time.
It then checks if any `PowerPlant` on the predefined list are expected to come online in the next contract period.
Plants matching that criterion are added to the Portfolio.
The Portfolio may include power plants built at different time steps of the simulation.

# Dependencies

No dependencies.

# Input from file

see also [PlantBuildingManager](./PlantBuildingManager.md)

* `Plants` Group list
    * `Efficiency` efficiency of this power plant
    * `NetCapacityInMW` peak power generation capacity in MW
    * `ActivationTime` Optional: if present marks the point in time when this power plant is activated; if missing the plant is active at simulation begin
    * `DeactivationTime` Optional: if present marks the point in time when this power plant is deactivated; if missing the plant is not deactivated any time
    * `Id` Optional: if present defines a name for this power plant. If not present, Id will be the number of the power plant in the portfolio
    * `Override` Optional Group: allows to override one or multiple parameters from the [Prototype](../Modules/PowerPlantPrototype.md) specifically for this power plant. Cannot override `FuelType` or `SpecificCo2EmissionsInTperMWH`.
        * `OutageFactor`: see [Prototype](../Modules/PowerPlantPrototype.md)
        * `OpexVarInEURperMWH`: see [Prototype](../Modules/PowerPlantPrototype.md)
        * `CyclingCostInEURperMW`: see [Prototype](../Modules/PowerPlantPrototype.md)
        * `MustRunFactor`: see [Prototype](../Modules/PowerPlantPrototype.md)

# Input from environment

None

# Simulation outputs

None

# Contracts

see [PlantBuildingManager](./PlantBuildingManager.md)

# Available Products

see [PlantBuildingManager](./PlantBuildingManager.md)

# Submodules

No submodules.

# Messages

see [PlantBuildingManager](./PlantBuildingManager.md)