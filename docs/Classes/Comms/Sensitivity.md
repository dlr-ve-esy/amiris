# In Short

A message that contains the sensitivity of a merit order forecast depending on additional demand or supply.
The type of sensitivity is unspecified in the message itself and should be known to the client.

# Details

The `Sensitivity` values depend on the change in demand or supply energy.
Values at any given energy delta are interpolated linearly between power-value tuples contained in the message.
Depending on the expected outcome, different interpolation methods can be used:

* `Direct` interpolation uses the slope of the current interval and interpolates from the origin to the requested energy delta.
* `Cumulative` interpolation starts at the highest value of the previous interval and then interpolates using the current slope.

Before interpolations can be done using the `getValue` method, the interpolation type must be set using `setInterpolationType()`.
`Sensitivity`'s field `multiplier` can be updated using the `updateMultiplier()` method.
In this way, new multiplier estimates can also be used on previously sent `Sensitivity` messages stored at the client side.
For a given amount of dispatched energy, one can obtain the assumed associated electricity price using the `getPriceInEURperMWH()` method.