# In short

`StateManager` is an interface that manages the discrete states used to describe the status of a [GenericDevice](./GenericDevice.md) within the dynamic programming optimisation.

# Details

## Assumptions

Regardless of the complexity of the state representation, the StateManager assigns an integer (state ID) to each possible state of a GenericDevice.
This state ID is assumed to represent the same state throughout an optimisation procedure, i.e., the state with ID `i` at time step `t` is identical to the state with ID `i` at time step `t+x`.
Furthermore, the `StateManager` assumes that the time and energy granularity does not change during the optimisation.
For subsequent optimisations, a constant time horizon is assumed.

## Operations

Using `initialise()`, `StateManager` must be provided with the very first TimePeriod of the optimisation horizon, before starting the actual optimisation.
The duration of the given TimePeriod also determines the time discretisation step width.
Then, `StateManager` can return the number of discretised time steps that correspond to the foresight horizon using `getNumberOfForecastTimeSteps()`.
It can also return a list of all TimeStamps to which these time steps correspond using `getPlanningTimes()`.

Since the dynamic programming algorithm steps though time consecutively, and each time step contains multiple calls to `StateManager`, its computation performance is improved by caching data of subsequent calculations at the same time step using the `prepareFor()` method.
At any prepared time, `StateManager` can tell which initial state IDs are available using `getInitialStates()`.
Depending on the result of `useStateList()`, this will either return a full list of all states, or only the first and the last state to iterate in between.
Note 
Based on a given initial state ID, `StateManager` can also tell which state IDs that can be reached at the prepared time without violating restrictions from the connected `GenericDevice` using `getFinalStates()`.
Again, depending on the result of `useStateList()`, this will either return a full list of the state IDs or only the first and the last state to iterate in between.
For any propose transition from state ID `i` to state ID `f` at the prepared time, `StateManager` can calculate the value of the state transition utilising its configured [AssessmentFunction](./AssessmentFunction.md) by using `getTransitionValueFor()`.

The dynamic programming [Optimiser](./Optimiser.md) will store the best available transition for each initial state using `updateBestFinalState()`.
All best follow-up states for all available initial states and time steps are thus stored in `StateManager`.
Also, the best associated assessment value of each state is stored in `StateManager`.
Using `getBestValuesNextPeriod()` the best value corresponding to a state in the next period can be obtained.

Once all state transitions have been assessed, `StateManager` can create an optimal dispatch schedule using `getBestDispatchSchedule()`.

# Input from file

See [StateManagerBuilder](./StateManagerBuilder.md)

# Child classes

* [StateOfCharge](./EnergyStateManager.md)
* [EnergyAndTime](./EnergyAndTimeStateManager.md)

# See also

* [GenericFlexibilityTrader](../Agents/GenericFlexibilityTrader.md)
* [Optimiser](./Optimiser.md)