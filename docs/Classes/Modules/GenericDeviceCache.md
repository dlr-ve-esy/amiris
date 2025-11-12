# In short

`GenericDeviceCache` caches properties of a [GenericDevice](./GenericDevice.md) at a certain time.
It simulates transitions between two internal states of charge (SOC) and returns the corresponding external energy delta (including self discharge, inflows / outflows, and (dis-)charging efficiency).

# Details

`GenericDeviceCache` is connected to a `GenericDevice` whose properties it caches and simulates.

## Assumptions

`GenericDeviceCache` assume that the properties of the connected `GenericDevice` are constant within the set time period.
It is assumed, that `prepareFor()` is called after `setPeriod()`.
Furthermore, all other methods are assumed to be called after `prepareFor()`.

**Attention**: To avoid performance impacts, these assumptions are not enforced or checked within the code.
It can lead to silent errors and wrong results if `GenericDeviceCache` is not used accordingly.

## Operations

Using `setPeriod()` the `GenericDeviceCache` is configured to assume a length of time steps according to the given time period.
Once the concrete time to cache for is then set using `prepareFor()`, `GenericDeviceCache` can be used to:

* determine the upper and lower energy content limits: `getEnergyContentUpperLimitInMWH()`, `getEnergyContentLowerLimitInMWH()`
* determine the maximum / minimum SOC reachable within the time step starting from a given SOC: `getMaxTargetEnergyContentInMWH()`, `getMinTargetEnergyContentInMWH()`
* determine the maximum energy delta for charging / discharging: `getMaxNetChargingEnergyInMWH()`, `getMaxNetDischargingEnergyInMWH()`
* simulate a transition between two SOC: `simulateTransition()`
 
The `GenericDeviceCache`, if used for load shifting, also ensures that prolonging of load shifts is performed correctly in case it is allowed. 
I.e. it ensures shifts to happen within power bounds.
See [GenericDevice](./GenericDevice.md) for further information on prolonging.

# See also

* [EnergyStateManager](./EnergyStateManager.md)
* [GenericDevice](./GenericDevice.md)