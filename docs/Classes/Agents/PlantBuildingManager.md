# 42 words

This agent installs and tears down conventional [PowerPlants](../Modules/PowerPlant.md) organised in [Portfolios](../Modules/Portfolio.md).
Each PlantBuildingManager is responsible for one type of conventional power plant.
It sends the Portfolios to connected [ConventionalPlantOperators](./ConventionalPlantOperator.md), which organise the power plant operation.

# Details

The `PlantBuildingManager` builds power plants for a given [PowerPlantPrototype](../Modules/PowerPlantPrototype.md).
It creates new [PowerPlants](../Modules/PowerPlant.md) and tear-down old ones.
Based on given inputs for the power plant prototypes, the `PlantBuildingManager` generates a new [Portfolio](../Modules/Portfolio.md) and sends it to a connected ConventionalPlantOperator.
The `PlantBuildingManager` is abstract and has no strategies on its own to set up the power plant portfolios.
These strategies are specified in the child classes.
Each `PlantBuildingManager` can handle one prototype only, and can thus handle only one conventional plant technology e.g. hard coal, lignite, nuclear, gasCC, gasTurbine.

# Dependencies

No dependencies per se - the `PlantBuildingManager` depends only on given input data.

# Input from file

- `Prototype` Group, defined in [PowerPlantPrototype](../Modules/PowerPlantPrototype.md)
- `PortfolioBuildingOffsetInSeconds` time offset between contracted delivery time of the portfolio and their actual first activation

# Input from environment

None, see subclasses

# Simulation outputs

None, see subclasses

# Contracts

- [ConventionalPlantOperator](./ConventionalPlantOperator.md): sends Portfolio

# Available Products

- PowerPlantPortfolio

# Submodules

* [PowerPlant](../Modules/PowerPlant.md)
* [Portfolio](../Modules/Portfolio.md)
* [PowerPlantPrototype](../Modules/PowerPlantPrototype.md)

# Messages

* [Portfolio](../Modules/Portfolio.md): This implements FAME's Portable interface, i.e. it can be added as content to a Message and need not be transformed to a message DataItem.

# See also

* [PredefinedPlantBuilder](./PredefinedPlantBuilder.md)