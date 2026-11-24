# In short

Each conventional `PowerPlant` is constructed by a [PlantBuildingManager](../Agents/PlantBuildingManager.md), based on a [PowerPlantPrototype](./PowerPlantPrototype.md).
It is constructed using a technical template `PrototypeData` - but individual plants may deviate from that template.

# Details

Different generation technologies are implemented with different time series and parameters by respective [PredefinedPlantBuilders](../Agents/PredefinedPlantBuilder.md).
Each power plant is built based on a [PowerPlantPrototype](./PowerPlantPrototype.md) and by configuring its efficiency and installed block power (in MW).
Using cost data for fuels and co2 emission certificates, the PowerPlant also calculates and announces its hourly `availablePower` and `marginalCostValue` to the [ConventionalPlantOperator](../Agents/ConventionalPlantOperator.md).
If a PowerPlant is dispatched via its `updateGeneration` method, it provides a [DispatchResult](./DispatchResult.md) comprising associated:

* actual electric generation
* associated cost
* emitted CO2
* and consumed fuel.

PowerPlant implements the `Portable` interface from FAME and can thus be transported in a message.
It also implements the `Comparable` interface and can thus be compared with respect to their efficiency.
