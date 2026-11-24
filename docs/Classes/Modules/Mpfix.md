# In Short

Mpfix is a type of [PolicyItem](./PolicyItem.md) holding all the [TechnologySet](../Comms/TechnologySet.md)-specific information for a `MPFIX` scheme, i.e. a fixed market premium.
Support payments equal the amount of generation eligible for support multiplied with the fixed market premium.
Not all hours of generation may be eligible for support, depending on the number of hours with negative prices that occurred during the accounting period.

# Details

Included parameters are

* `Premium`: The fixed market premium of the [TechnologySet](../Comms/TechnologySet.md) which is paid on top to market revenues and given in EUR/MWh of power produced.
* `IsEligibleAtNegativePrices`: If set to `1`, support is also paid at times of negative prices. Otherwise, if set to `0`, no support is paid in such situations.

# See also

[PolicyItem](./PolicyItem.md)