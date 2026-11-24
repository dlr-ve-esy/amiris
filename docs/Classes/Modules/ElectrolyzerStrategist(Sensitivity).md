# In Short

`ElectrolyzerStrategist` is an abstract base class hosting common tasks for `Electrolyzer` strategists.
It extends the basic [Strategist](./Strategist(Electrolysis).md) class.
It has one concrete child class:

* [ProductionTarget](./ProductionTarget.md) - Determines an optimal dispatch strategy, thereby seeking to fulfill a hydrogen production target while considering sensitivity forecasts

# Details

`ElectrolyzerStrategist` create [BidSchedules](./BidSchedule.md) for an [Electrolyzer](Electrolyzer.md) unit using sensitivity-based electricity price as well as hydrogen price forecasts.
It hereby accounts for optional support payments for the produced hydrogen from [HydrogenSupportPolicy](../Agents/HydrogenSupportPolicy.md) as these effect opportunity costs of hydrogen production.

## Input

* `Strategy` parent group for the dispatch strategy
  * `ProductionTarget` optional group defined in [ProductionTarget](./ProductionTarget.md)
  * `PlanningHorizonInHours` amount of foresight used in dispatch planning
  * `Type` strategist type; allowed values are `PRODUCTION_TARGET` for a [ProductionTarget](./ProductionTarget.md) strategist

# Child classes

* [ProductionTarget](./ProductionTarget.md)

# See also

* [Electrolyzer](Electrolyzer.md)
* [ElectrolysisTraderSensitivity](../Agents/ElectrolysisTraderSensitivity.md)
* [Strategist(Electrolysis)](./Strategist(Electrolysis).md)
* [HydrogenSupportPolicy](../Agents/HydrogenSupportPolicy.md)