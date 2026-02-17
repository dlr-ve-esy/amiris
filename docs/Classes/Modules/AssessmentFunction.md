# In short

`AssessmentFunctions` are used by [StateManagers](./StateManager.md) to assess the value of [GenericDevice](./GenericDevice.md) operations.

# Details

## Assumptions

It is assumed that `prepareFor()` is called once per time step and before `assessTransition()`.

## Operations

`AssessmentFunctions` often use forecast data (e.g. electricity price forecasts) to assess the value of a specified operation.
Using `getMissingForecastTimes()` `AssessmentFunction` will return the TimeStamps that forecasts are missing for an assessment.
In return, `storeForecast()` can be used to feed `AssessmentFunction` with corresponding forecasts data.
To remove outdated forecast data, `clearBefore()` can be used.
With `getTargetType()` the type of the assessment goal can be obtained (i.e. maximisation or minimisation).
Using `getSensitivityType()`, the type of Forecast that is required by the `AssessmentFunction` can be checked.
The `getMultiplier()` method returns the latest multiplier (see [FlexibilityAssessor](./FlexibilityAssessor.md)) applied to energy deltas during optimisation.

Once `prepareFor()` was called to set the specific time for assessments, `assessTransition()` can be called to assess the value of one or multiple transitions.

`getElectricityPriceAt` will return the electricity price assumed by the AssessmentFunction at the given time and dispatched energy.

# Input from file

See [AssessmentFunctionBuilder](./AssessmentFunctionBuilder.md)

# Child classes

* [SensitivityBasedAssessment](./SensitivityBasedAssessment.md)

# See also

* [GenericFlexibilityTrader](../Agents/GenericFlexibilityTrader.md)
* [GenericDevice](./GenericDevice.md)
* [StateManager](./StateManager.md)