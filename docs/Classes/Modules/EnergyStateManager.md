# In short

`EnergyStateManager` covers states of a [GenericDevice](./GenericDevice.md) along one dimension, representing its internal energy content or state of charge (SOC).

# Details

## Assumptions

Properties of the connected `GenericDevice` are assumed to be constant during the time steps.
Thus, the time discretisation of the TimeSeries properties of a `GenericDevice` should match (or be an integer multiple of) the time resolution of the dispatch optimisation.

To consider the SOC value at the end of the foresight horizon, `EnergyStateManager` can be parametrised with [WaterValues](./WaterValues.md).
If no `WaterValues` are provided, the value of any SOC level will be assumed to be zero.

## Operations

### TimeSeries analysis

When `EnergyStateManager`'s `initialise()` method is called, it will analyse the properties of the connected `GenericDevice` and, based on the requested energy resolution, determine how many discretisation steps are required along the time and energy dimensions.
It will detect if the `GenericDevice` has self discharge.

### State calculations

States in `EnergyStateManager` are one-dimensional compact.
This enables an efficient state representation, a full state list is not required.  
Hence,

* `useStateList()` will return `false`
* `getInitialStates()` and `getFinalStates()` will return only the IDs of the first and last state to be considered, not the full list, giving [Optimiser](./Optimiser.md) a higher speed.

`EnergyStateManager` utilises a [StateDiscretiser](./StateDiscretiser.md) to match energy content to discretised states.

### Caching

If self-discharge is not modelled, the [TransitionEvaluator](./TransitionEvaluator.md) utilised by `EnergyStateManager` will operate faster and cache all transition values for a given time step during `prepareFor()`.
In any case, `EnergyStateManager` pre-caches the properties of its `GenericDevice` using a [GenericDeviceCache](./GenericDeviceCache.md) at any given time step.
**Warning**: If the `GenericDevice`'s lower or upper energy content limit is not constant, the algorithm will not consider changes in the number of states at the end of its planning interval.
This can lead to imperfect planning results and the `GenericDevice` might temporarily operate outside of its (changed) energy limits.

### Dispatch scheduling

The [StateEvaluations](./StateEvaluations.md) module used by `EnergyStateManager` will ensure that no energy is lost or gained during dispatch and find the optimal state path based on the previously evaluated states.

See also [StateManager](./StateManager.md).


# Input from file

See [StateManagerBuilder](./StateManagerBuilder.md)

# See also

* [StateManager](./StateManager.md)
* [StateDiscretiser](./StateDiscretiser.md)
* [TransitionEvaluator](./TransitionEvaluator.md)
* [StateEvaluations](/StateEvaluations.md)
* [GenericDevice](./GenericDevice.md)
* [GenericDeviceCache](./GenericDeviceCache.md)
* [Optimiser](./Optimiser.md)
* [WaterValues](./WaterValues.md)