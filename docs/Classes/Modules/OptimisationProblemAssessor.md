# In Short

`OptimisationProblemAssessor` in package `dynamicProgramming` is called by [Optimiser](./Optimiser.md) when no valid transition can be found at a certain time period within the optimisation horizon.
Its sole purpose is to create a meaningful error message that helps users in debugging their simulation.

# Details

`OptimisationProblemAssessor` assesses data provided by a [StateManager](./StateManager.md) prepared for a certain time period that did result in no valid transitions.

# See also

* [Optimiser](./Optimiser.md)
* [StateManager](./StateManager.md)
