# In Short

Mpvar is a type of [PolicyItem](./PolicyItem.md) holding all the [TechnologySet](../Comms/TechnologySet.md)-specific information for a `MPVAR` scheme, i.e. a variable market premium scheme.
Support payments equal the amount of generation eligible for support multiplied with the market premium.
The market premium equals the difference between the levelised cost of electricity (LCOE) of the [TechnologySet](../Comms/TechnologySet.md) and the market value of the related energy carrier.
Market values are evaluated after accounting period.
Market premia are not negative.
Not all hours of generation may be eligible for support, depending on the number of hours with negative prices that occurred during the accounting period.

# Details

Pieces of information included are
* `Lcoe`: The lcoe of the [TechnologySet](../Comms/TechnologySet.md) which (by assumption) equals to its value to be applied in a variable ex post market premium scheme and given in EUR/MWh of power produced.
* `IsEligibleAtNegativePrices`: If set to `1`, support is also paid at times of negative prices. Otherwise, if set to `0`, no support is paid in such situations.

# See also

[PolicyItem](./PolicyItem.md)