<!-- SPDX-FileCopyrightText: 2026 German Aerospace Center <amiris@dlr.de>

SPDX-License-Identifier: Apache-2.0 -->
# Upgrading

## 4.0.0

### GenericFlexibilityTrader: Assessment.Types Renamed

Names of assessment function types of `GenericFlexibilityTrader` were renamed:

* `SINGLE_AGENT_MIN_SYSTEM_COST` &rarr; `MIN_SYSTEM_COST`
* `SINGLE_AGENT_MAX_PROFIT` &rarr; `MAX_PROFIT`

### GenericFlexibilityTrader: MIN_SYSTEM_COST Assessment Uses Prices

Previously, the AssessmentFunction `MIN_SYSTEM_COST` used the exact marginal cost of bids to calculate the system cost.
In this version, however, system costs are estimated from the bid prices.
Therefore, bids represent an operator's "true" marginal costs.
Mark-ups and mark-downs merely help modelling the effects of shut-down and ramping costs, for example.

### PlantBuilder: Change Availability Attributes to Outage

The attributes `UnplannedAvailabilityFactor` $a$ and `PlannedAvailability` $a'$ whose product described the total available share of capacity of `PredefinedPlantBuilder` and `IndividualPlantBuilder` were replaced by a single deterministic `OutageFactor` $o$.
The following formula defines the `OutageFactor` as the share of capacity which is **not** available:

$o = 1 - a * a'$

Adapt the attribute names and values accordingly.

## 3.5.0

This version features the `GenericFlexibilityTrader` which will replace `StorageTrader` in a future release.
We recommend to switch to `GenericFlexibilityTrader` as it also offers more comprehensive and flexible parametrisation:
* asymmetric charging / discharging power,
* consideration of inflows and outflows,
* self discharging,
* time-dependent upper and lower capacity limits.

To use this feature, replace `StorageTrader` with `GenericFlexibilityTrader` and in the yaml file replace 

```yaml
Type: StorageTrader
Id: 7
Attributes:
  Device:
    EnergyToPowerRatio: 5.0
    SelfDischargeRatePerHour: 0.01
    ChargingEfficiency: 0.5
    DischargingEfficiency: 0.9
    InitialEnergyLevelInMWH: 2000
    InstalledPowerInMW: 10000
  Strategy:
    StrategistType: SINGLE_AGENT_MIN_SYSTEM_COST
    ForecastPeriodInHours: 168
    ScheduleDurationInHours: 24
    SingleAgent:
      ModelledChargingSteps: 100
```

by

```yaml
Type: GenericFlexibilityTrader
Id: 7
Attributes:
  Device:
    GrossChargingPowerInMW: 20000.  # equals former "InstalledPowerInMW" / "ChargingEfficiency"
    NetDischargingPowerInMW: 9000.  # equals former "InstalledPowerInMW" * "DischargingEfficiency"
    ChargingEfficiency: 0.5
    DischargingEfficiency: 0.9
    SelfDischargeRatePerHour: 0.01
    NetInflowPowerInMW: 0.
    EnergyContentUpperLimitInMWH: 50000  # equals former "InstalledPowerInMW" * "EnergyToPowerRatio"
    InitialEnergyContentInMWH: 2000  # former "InitialEnergyLevelInMWH"
  Assessment:
    Type: SINGLE_AGENT_MIN_SYSTEM_COST  # former "StrategistType"
  StateDiscretisation:
    Type: STATE_OF_CHARGE
    PlanningHorizonInHours: 168  # former "ForecastPeriodInHours"
    EnergyResolutionInMWH: 100  # equals former "InstalledPowerInMW" / "ModelledChargingSteps"
  Bidding:
    Type: ENSURE_DISPATCH
    SchedulingHorizonInHours: 24  # former "ScheduleDurationInHours"
```

## 3.0.0

### String Sets

This version requires new feature `string_set` provided by `fameio` > v2.3.
Thus, update `fameio` accordingly.
If not yet present, add a new `StringSets` section to your scenario.

#### FuelType

Add a new StringSet `FuelType` to your scenario listing all fuel names used by your agents.
Example:

```yaml
StringSets:
  FuelType:
    Values: ['Oil', 'Hydrogen', 'MySpecialFuel']
```

#### Set -> PolicySet

Input parameter `Set` was renamed to `PolicySet` for agent types `RenewablePlantOperator` and its children, as well as for `SupportPolicy`.
Therefore, rename occurrences accordingly.
In addition, add a new StringSet `PolicySet` to your scenario listing all policy sets available to your agents.
Example:

```yaml
StringSets:
  PolicySet:
    Values: ['WindOn', 'Biogas', 'MyPolicySet']
```

#### OwnMarketZone & ConnectedMarketZone -> MarketZone

Input parameters `OwnMarketZone` and `ConnectedMarketZone` for agent types `DayAheadMarketMultiZone` were both renamed to `MarketZone`.
Rename occurrences accordingly.
In addition, add a new StringSet `MarketZone` to your scenario listing all market zones.
Example:

```yaml
StringSets:
  MarketZone:
    Values: ['DE', 'AT', 'FR']
```

### Fixed typo

A typo in the often used input parameter `InvestmentExpensesesInEURperMW` was fixed to `InvestmentExpensesInEURperMW`.
Update your schema files and scenarios, and if necessary, adjust you scripts if these refer to this parameter name explicitly.

### Remove ForecastRequestOffsets

1. Update your scenarios by removing Agent input Attributes `ElectricityForecastRequestOffsetInSeconds`, `HydrogenForecastRequestOffsetInSeconds`, and `ForecastRequestOffsetInSeconds` from `StorageTrader`, `ElectrolysisTrader`, `MeritOrderForecaster`, and `PriceForecaster`.
1. Contracts: Add a new Contract from `DayAheadMarketSingleZone` to your Forecaster(s), sending a `GateClosureInfo` at `-30` with a `DeliveryIntervalInSteps: 3600`.
1. Contracts: Change the `FirstDeliveryTime` of all Contracts with Product `GateClosureInfo` to `-30`.
1. `DayAheadMarketSingleZone`: Change the Attribute `GateClosureInfoOffsetInSeconds` to 31. 

## 2.0.0

### Minimum JDK 11

This version drops support for JDK 8, 9, and 10.
If you have a higher JDK already installed, no steps are required.
Check your JDK version with `java --version` (or `java -version` on some systems). 
If your Java version is below 11, please download and install a recent JDK from e.g. [here](https://adoptium.net/).

Update your input scenarios and contracts.
Several input parameters (field names and contract product names) changed or were removed.

Check your scripts that digest AMIRIS outputs.
Several output columns were renamed.