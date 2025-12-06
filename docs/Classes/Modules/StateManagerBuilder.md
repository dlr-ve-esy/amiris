# In short

Builds [StateManager](./StateManager.md) from given input parameters

# Input from file

* `Type`: enum, name of the assessment function that is to be instantiated
* `PlanningHorizonInHours`: double value, time length of the foresight horizon used when optimising the dispatch
* `EnergyResolutionInMWH`: double value, granularity of the energy discretisation, smaller values lead to more precise results but quadratically increasing calculation effort
* `WaterValues`: optional list of groups to specify water values for the optimisation at the end of the foresight horizon, see [WaterValues](./WaterValues.md)

# Available Types

* `STATE_OF_CHARGE`: Energy states of a device are represented in one dimension, see [EnergyStateManager](./EnergyStateManager.md)
* `ENERGY_AND_TIME`: Represent energy states and current shifting time of a device in two dimensions, see [EnergyAndTimeStateManager](EnergyAndTimeStateManager.md)

# See also

* [StateManager](./StateManager.md)
* [WaterValues](./WaterValues.md)