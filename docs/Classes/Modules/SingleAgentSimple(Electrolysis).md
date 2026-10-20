# In short

`SingleAgentSimple` is a kind of [ElectrolyzerStrategist](./ElectrolyzerStrategist.md).
Creates [BidSchedule](./BidSchedule.md) using electricity and hydrogen price forecasts and an estimate for the change of electricity price due to change in demand.
SingleAgentSimple tries to utilise hours with high economic potential within the forecast period to produce a given amount of hydrogen.

# Input

* `HydrogenProductionTargetInMWH` Time series specifying the targeted *total* hydrogen production within the `ProductionTargetIntervalInHours`; **Important:** Always synchronise data points with production target interval (e.g.: production interval X hours &rarr; total hydrogen production target specifications every X hours)
* `ProductionTargetIntervalInHours` Time resolution of `HydrogenProductionTargetInMWH` time series data points; **Beware**: If this time resolution does not match that of the time series and time series is anything other than a constant &rarr; results will be wrong! *Attention*: Value should not be smaller than forecast period!
* `PriceSensitivityFunction` Estimate of how the electricity price rises with increasing the demand at a given price (i.e. price change per demand change over price); given as coefficients in ascending order of degree, i.e. a + b·x + c·x² + … .
* `PowerStepInMW` Energy resolution of the algorithm: the smaller, the more steps are required to complete the algorithm

# Details

SingleAgentSimple tries to fulfil its target of total hydrogen production within a specified time interval (production interval).
The production interval is assumed to be of constant length.
The production target of each interval can vary, as specified by the `HydrogenProductionTargetInMWH` time series.
As mentioned above, the production interval and target must have the same temporal basis, i.e. if the production interval is X hours, the time series must contain targets every X hours (each covering the required total production within given X hours).
SingleAgentSimple searches the forecast period for hours with highest (positive) economic potential, i.e. where expected revenues for selling hydrogen exceed cost for electricity required for its production.
It will bid for electricity at a price that avoids economic losses.
Thus, if hydrogen price equivalent is low compared to electricity prices, SingleAgentSimple might fail to achieve production targets.
Depending on the length of the forecast period and dispatch schedule, SingleAgentSimple may decide to overproduce within one production interval, if it has foresight on the next production interval as well (and prices are higher there).
The given `PriceSensitivityFunction` should match the overall merit order of the simulation.
This, however, may be hard to obtain.
We suggest to use this function with care and to make sure that the specified polynomial is not too high in order and rather smooth in the expected price range (few extrema, not negative anywhere!).
Thus, it might be better to use a more generic function and very low order polynomial (order lesser or equal to 2) with less precision, rather than trying to obtain a fit to the actual merit order for each simulation.

# See also

* [Strategist](./ElectrolyzerStrategist.md)
* [Electrolyzer](./Electrolyzer.md)