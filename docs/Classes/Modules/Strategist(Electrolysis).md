# In Short

`Strategist` is a basic abstract class for strategist operating a flexible electrolysis unit, thereby making used of sensitivity-based forecasts.

# Details

`Strategist` covers storing of forecasts for electricity prices or merit orders and handles basic strategy parameters, such as the schedule duration.
It also defines how to calculate a [BidSchedule](./BidSchedule.md).

## Input

* `Assessment` Group specifying assessment function; see [AssessmentFunctionBuilder](./AssessmentFunctionBuilder.md); Note: Only value `MAX_PROFIT` for a profit-maximising sensitivity-based assessment strategy is allowed, see [MaxProfit](./MaxProfit.md). 
* `Bidding` Group specifying bidding behaviour
  * `ScheduleDurationInHours` Number of hours each created schedule is viable; should be less than or equal to the forecast period
  * `BidToleranceInEURperMWH` Optional input to define by what amount the offered price shall be increased when buying electricity
* `Strategy` Group defining dispatch planning strategy, see [ElectrolyzerStrategist](./ElectrolyzerStrategist(Sensitivity).md)

# Child classes

* [ElectrolyzerStrategist](./ElectrolyzerStrategist(Sensitivity).md)

# See also

* [ElectrolyzerStrategist](./ElectrolyzerStrategist(Sensitivity).md)
* [AssessmentFunctionBuilder](./AssessmentFunctionBuilder.md)