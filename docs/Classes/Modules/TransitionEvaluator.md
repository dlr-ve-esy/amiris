# In short

`TransitionEvaluator` evaluates transitions. It can cache results for similar transitions if no self discharge occurs.
With self discharge, however, energy losses depend on the exact states involved and not only on the state delta.

# Details

When `prepareFor()` is called, `TransitionEvaluator` will call `prepareFor` on the connected `GenericDeviceCache` and the used `AssessmentFunction`.
Without self discharge, values for all possible transitions in the energy space are evaluated and cached.
Then, upon calling `getTransitionValueFor()`, `TransitionEvaluator` can return the cached values - or in case of non-zero self discharge - directly calculate the transition value.

# See also

* [StateManager](./StateManager.md)
* [GenericDeviceCache](./GenericDeviceCache.md)
* [AssessmentFunction](./AssessmentFunction.md)