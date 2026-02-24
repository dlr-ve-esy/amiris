# In short

`EnergyAndTimeStateManager` is a [StateManager](./StateManager.md) that covers states of a [GenericDevice](./GenericDevice.md) along two dimensions, representing its energy content (state of charge) and time out of balance (shift time).

# Details

## Assumptions

Properties of the connected `GenericDevice` are assumed to be constant during the time steps.
Thus, the time discretisation of the TimeSeries properties of a `GenericDevice` should match (or be an integer multiple of) the time resolution of the dispatch optimisation.

The usage of [WaterValues](./WaterValues.md) and self discharge are not compatible with an `EnergyAndTimeStateManager`.
If they are parameterised for the connected [StateManagerBuilder](./StateManagerBuilder.md) or [GenericDevice](./GenericDevice.md), this will raise an error.

## Operations

### TimeSeries analysis

When `EnergyAndTimeStateManager`'s `initialise()` method is called, it will analyse the properties of the connected `GenericDevice` and, based on the requested energy resolution, determine how many discretisation steps are required along the time and energy dimensions.

### State calculations

States in `EnergyStateAndTimeManager` are two-dimensional.
See [StateDiscretiser](./StateDiscretiser.md) for an in-depth description of how states are defined and which state transitions are valid.

An `EnergyStateAndTimeManager` will always require a full state list.
Hence,

* `useStateList()` will return `true`
* `getInitialStates()` and `getFinalStates()` will return the full list of all feasible states

Note that using the full state list comes at the expense of a lower speed for the [Optimiser](./Optimiser.md).

### Caching

`EnergyStateAndTimeManager` utilises a [TransitionEvaluator](./TransitionEvaluator.md) that will cache all transition values for a given time step during `prepareFor()`.
In any case, `EnergyStateManager` pre-caches the properties of its `GenericDevice` using a [GenericDeviceCache](./GenericDeviceCache.md) at any given time step.
**Warning**: If the `GenericDevice`'s lower or upper energy content limit is not constant, the algorithm will not consider changes in the number of states at the end of its planning interval.
This can lead to imperfect planning results and the `GenericDevice` might temporarily operate outside of its (changed) energy limits.

### Dispatch scheduling

The [StateEvaluations](./StateEvaluations.md) module used by `EnergyStateAndTimeManager` will ensure that no energy is lost or gained during dispatch and find the optimal state path based on the previously evaluated states.

# Input from file

See [StateManagerBuilder](./StateManagerBuilder.md)

# See also

* [StateManager](./StateManager.md)
* [EnergyStateManager](./EnergyStateManager.md)
* [StateDiscretiser](./StateDiscretiser.md)
* [TransitionEvaluator](./TransitionEvaluator.md)
* [StateEvaluations](/StateEvaluations.md)
* [GenericDevice](./GenericDevice.md)
* [GenericDeviceCache](./GenericDeviceCache.md)
* [Optimiser](./Optimiser.md)
* [WaterValues](./WaterValues.md)