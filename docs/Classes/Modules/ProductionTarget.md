# In Short

`ProductionTarget` is type of [ElectrolyzerStrategist](./ElectrolyzerStrategist(Sensitivity).md).
It schedules an electrolysis unit to fulfill a hydrogen production target within a specified time interval 
while considering electricity price sensitivity and hydrogen price forecasts.

# Details

## Overview

ProductionTarget determines an optimal dispatch strategy by:
1. Tracking hydrogen production deficits or surpluses relative to production targets within each planning period
2. Optimising electricity consumption to minimise costs while meeting production targets
3. Using sensitivity forecasts to account for market impact when multiple flexibility options compete

## How It Works

### Production Target Tracking

The strategist maintains a rolling production period based on the `ProductionTargetIntervalInHours` parameter:
- It tracks whether hydrogen production targets have been met for each interval
- Deficits or surpluses from previous intervals carry forward to ensure long-term target fulfillment

### Dispatch Optimisation

The optimisation algorithm works as follows:
1. Calculate the remaining hydrogen production target within the forecast period
2. Determine the economic potential for each hour (hydrogen sale opportunity cost minus electricity price)
3. Distribute electrolysis dispatch to hours with highest economic potential
4. Ensure the total hydrogen production matches the target across the entire forecast period

The key principle is to consume electricity in hours, where the difference between hydrogen sale revenue and electricity cost is highest.

### Sensitivity-Aware Dispatch

As it is integrated with a `SensitivityForecaster`, the strategist receives:
- Dispatch multipliers that estimate the joint impact of all flexibility options
- Updated electricity price assessments (i.e. marginal cost for the electrolyzer) reflecting market competition
- This allows the strategist to optimise considering how its dispatch as well as the estimated effect of competitors affects overall market prices

## Input Parameters

- `HydrogenProductionTargetInMWH`: time series; Time series of hydrogen production targets (MWh) for each time period
- `ProductionTargetIntervalInHours`: int; Interval in hours within which production targets must be fulfilled

# See also

- [ElectrolyzerStrategist(Sensitivity)](./ElectrolyzerStrategist(Sensitivity).md)
- [Strategist(Electrolysis)](./Strategist(Electrolysis))
- [ElectrolysisTraderSensitivity](./ElectrolysisTraderSensitivity.md)
