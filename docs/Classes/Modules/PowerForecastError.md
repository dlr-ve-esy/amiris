# In short

Provides random errors for power forecasting using a normal distribution.

# Details

Creates random error factors from a normal distribution with given `Mean` and `StandardDeviation` using the provided random number generator.
The random error factors are applied to a given perfect foresight power to generate power forecasts with errors.
Generated error factors are multiplied with the true power potential.
Thus, the configuration of `Mean` and `StandardDeviation` refer to the **relative** error of the power potential.

## Input from file

* `Mean` Relative offset of the power forecasts
* `StandardDeviation` of the relative power forecasting errors