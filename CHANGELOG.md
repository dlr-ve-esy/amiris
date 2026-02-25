<!-- SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>

SPDX-License-Identifier: CC0-1.0 -->
# Changelog

## [4.1.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v4.1.0) - TBA
### Changed
- `EnergyStateManager`: also use `StateDiscretiser` to reduce code duplications #227 (@dlr-cjs, @dlr_jk)
- docs: Improve get-started section based on user feedback #210 (@dlr-cjs)
- tests: improved unit tests for merit order kernel #218 (@dlr-cjs)

### Added
- `GenericFlexibilityTrader`: Implement load shifting with generic flexibility #205 (@dlr-cjs, @dlr_jk)
- GenericFlexibility: Add variable cost as optional input #206 (@dlr-cjs, @dlr_jk)
- GenericFlexibility: Add maximum shift time and energy shift prolonging as optional inputs #207 (@dlr-cjs, @dlr_jk)
- GenericFlexibilityTrader: Add output column `ElectricityPricePredictionInEURperMWH` #219 (@dlr-cjs, @jgower11)
- agents.forecast.sensitivity: Add `SensitivityForecasterFile` that provides forecasts in sensitivity format read from file  #220 (@dlr-cjs)
- `DayAheadMarketMultiZone`: Add Product `TransmissionCapacities` to be sent out to connected forecasters #143 (@dlr-cjs, @dlr_fn, @dlr_jk, @milenasipovac)
- add new Ability `MarketCouplingClient` to enable market coupling interactions also from forecasters  #143 (@dlr-cjs, @dlr_fn, @dlr_jk, @milenasipovac)
- `MarketForecaster`: add ability to interact with `MarketCoupling` #143 (@dlr-cjs, @dlr_fn, @dlr_jk, @milenasipovac)
- CI: also check compatibility with JDK 25
- docs: add content milestones to `History.md` #198 (@dlr-cjs)

### Fixed
- docs: Update Paths to example scenarios #217 (@dlr-cjs)
- Correct crash in `EnergyStateManager` due to missing offset in energy levels #225 (@dlr-cjs, @dlr_jk)
- `LoadShiftingTrader`: Fix shift time initialisation and bid price limits #191 (@dlr-cjs, @dlr_jk)
- `FlexibilityAssessor`: Fix improper formatted warning messages #204 (@dlr-cjs)
- `MeritOrderKernel`: Fix inconsistent clearing result on cut at same power #221 (@dlr-cjs)
- `MarketCoupling`: Fix accidental interaction of multiple market coupling events #226 (@dlr-cjs, @dlr_jk)
- `GenericDevice`: Fix too early triggering of prolonging #229 (@dlr-cjs, @dlr_jk)
- license headers: use incremental dates in SPDX headers #223 (@dlr-cjs)

## [4.0.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v4.0.0) - 2025-09-09
_If you are upgrading: please see [`UPGRADING.md`](UPGRADING.md)_

### Changed
- **Breaking**: Rename `GenericFlexibilityTrader` assessment function types #166 (@dlr-cjs, @dlr_jk)
- **Breaking**: PlantBuilder: Replace `PlannedAvailability` and `UnplannedAvailabilityFactor` attributes with `OutageFactor` #148 (@dlr-cjs, @dlr_jk, @dlr_fn)
- **Breaking**: Use bid prices to estimate system cost in `GenericFlexibilityTrader`'s assessment function `MIN_SYSTEM_COST` #196 (@dlr-cjs, @dlr_jk, @dlr_fn)
- `PriceforecasterApi` forwards average of multiple price forecasts #162 (@dlr_fn, @dlr-cjs)
- Update links in documentation to main repository #202 (@dlr_fn, @dlr-cjs)
- Update formatting of templates #179 (@dlr-cjs)
- Update `spotless-maven-plugin` to `2.44.5` #186 (@dlr-cjs, @dlr_fn)
- Update CITATION.cff and release workflow #200 (@dlrc-js)

### Added
- Docs: Add wiki content to folder `docs` #184 (@dlr-cjs, @dlr_fn)
- Docs: provide overview on AMIRIS model history #192 (@dlr_fn)
- Docs: provide information on how to update AMIRIS and examples !199 (@dlr_fn)

### Fixed
- Fix crash in `MarketCoupling` if market gets emptied due to transfers #185 (@dlr-cjs)
- Avoid crash in `EnergyStateManager` due to varying energy levels over time - log a warning instead #199 (@dlr-cjs, @dlr_jk)

## 3.7.1 - 2025-08-14
### Fixed
- Fix deletion of latest deploy artifact when creating a merge request from main branch #130  (@dlr-cjs, @dlr_fn)

## [3.7.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.7.0) - 2025-08-12
### Changed
- Update schema to feature metadata #193 (@dlr-cjs)

### Added
- Add `HeuristicStorageTrader` that operates a `GenericDevice` using heuristic median-based strategy #180 (@dlr-cjs)

### Fixed
- Fix non-avoidance of scarcity events within `FullAssessor` #189 (@dlr_fn, @dlr-cjs, @dlr-jk)
- Fix multiplier to One for `HeuristicStorageTrader` !200 (@dlr-cjs)

## [3.6.2](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.6.2) - 2025-08-04
### Fixed
- Fix allocation of awarded power when market coupling is enabled #187 (@dlr-cjs, @dlr_fn)

## [3.6.1](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.6.1) - 2025-07-23
### Fixed
- Fix crash on minor numerical deviations of `PowerPlant` load levels #183 (@dlr-cjs)
- Fix NullPointerException for unregistered clients at `SensitivityForecaster` #181 (@dlr-cjs)
- Fix missing side effect in `MarketClearing` if `SupplyBook` is empty #182 (@jgower11, @dlr-cjs)

## [3.6.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.6.0) - 2025-07-16
### Changed
- Rename `forecast.ForecastClient` to `DamForecastClient` #121 (@dlr-cjs)
- Enable a single `MarketForecaster` to handle forecasts for both MeritOrderRequests and PriceForecastRequests at the same time #121 (@dlr-cjs)
- Move functionality from `MeritOrderForecaster` to `MarketForecaster` and mark the first as deprecated #121 (@dlr-cjs)
- Move functionality from `PriceForecaster` to `MarketForecaster` and mark the first as deprecated #121 (@dlr-cjs)
- Make `PriceForecasterApi` compatible with new interface `SensitivityForecastProvider` #168 (@dlr-cjs)
- Allow `PriceForecasterApi` to update SensitivityForecasts if required #163 (@dlr-cjs, @dlr_fn)
- Change badges shown in Readme #160 (@dlr-cjs, @dlr_fn)

### Added
- Add `SensitivityForecaster` that provides sensitivity forecasts for `GenericFlexibilityTrader` #158 (@dlr_jk, @dlr-cjs)
- Add new class `HouseholdPvTraderExternal` using external model for household marketing of PV-storages #152 (@dlr_elghazi)
- Add new class `EvTraderExternal` using external model for household marketing of electric vehicles #152 (@dlr_elghazi)
- Add new package `agents.policy.hydrogen` to depict support policies for hydrogen #169 (@dlr_jk, @dlr-cjs)
- Add new Ability `DamForecastProvider` to replace class `Forecaster` #121 (@dlr-cjs)
- Add new BidScheduler `StorageContentValue` using the expected change of value in a flexibility device to derive its bids #167 (@dlr-cjs)
- Add option to specify water values for `GenericFlexibilityTrader` #159 (@dlr-cjs, @dlr_jk, @dlr_fn)
- Add option to specify must-run timeseries for conventional power plants #174 (@dlr-cjs, @dlr_jk, @dlr_fn)
- Add metadata.json to describe project with metadata #147 (@dlr-cjs)

### Remove
- Remove class `Forecaster` #121 (@dlr-cjs)

### Fixed
- Fix shedding of PPA electricity in `GreenHydrogenTraderMonthly` !174 (@dlr-cjs, @dlr_jk)
- Fix energy distribution among clients of `AggregatorTrader` with same effective bidding price #176 (@dlr-cjs, @dlr_jk)

## [3.5.1](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.5.1) - 2025-05-14
### Fixed
- Fix incongruent energy levels in `EnergyStateManager` that could cause a crash #164 (@dlr-cjs)

## [3.5.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.5.0) - 2025-05-05
_If you are upgrading: please see [`UPGRADING.md`](UPGRADING.md)_
### Changed
- Change data type of `ImportCostInEURperMWH` to time series #150 (@dlr_jk)
- Rename `Strategist` in `dynamicProgramming` to `Optimiser` #156 (@dlr-cjs)
- Improve README #149 (@LeonardWilleke, @dlr_fn, @dlr-cjs)
- Reuse cache in CI pipelines of same branch #157 (@dlr-cjs)

### Added
- Add new package `dynamicProgramming` for controlling dynamic programming algorithms for flexibility scheduling #116 (@dlr-cjs, @dlr_fn, @dlr_jk)
- Add `GenericDevice` that represents a generic electrical flexibility #115 (@dlr-cjs, @dlr_fn, @dlr_jk)
- Add AssessmentFunction `MinSystemCost` #154 (@dlr-cjs)
- Add AssessmentFunction `MaxProfit` #154 (@dlr-cjs)
- Add Trader `GenericFlexibilityTrader` to operate `GenericDevice` #116 (@dlr-cjs, @dlr_fn, @dlr_jk)
- Add optional "Refinancing" parameters for `GreenHydrogenTrader` #149 (@dlr_jk)
- Add JSONable interface for UrlModelService input classes #151 (@dlr_elghazi, @dlr-cjs)
- Add code formatting using spotless #117 (@dlr-cjs, @dlr_fn, @dlr_jk)

### Fixed
- ReadMe: Fixed typo in Acknowledgements (@dlr_fn)

## [3.4.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.4.0) - 2025-02-21
### Changed
- Improve README.md to be more attractive for first contact #142 (@dlr-cjs)
- Update CONTRIBUTING.md #142 (@dlr-cjs)

### Added
- Add new agent `LoadShiftingTrader` that markets a portfolio of loads eligible for load shifting at the day-ahead market #137 (@dlr_jk, @dlr-cjs)
- Add new agent `PriceForecasterApi` enabling calling external forecasting models #144 (@dlr_fn, @dlr-cjs)

## [3.3.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.3.0) - 2025-01-29
### Added
- Add new storage strategy `MAX_PROFIT_PRICE_TAKER` #139 (@dlr_fn, @dlr-cjs)
- Add option to specify `ForecastUpdateType` #140 (@dlr_fn, @dlr-cjs)
- Add link to AMIRIS Open Forum in documentation #136 (@dlr_fn)

### Fixed
- MarketCoupling: market coupling algorithm works as expected with market zones defined as StringSets #138 (@dlr_elghazi, @dlr_fn)

## [3.2.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.2.0) - 2024-12-04
### Changed
- Update to FAME-Core v2.0.1 & FAME-Io v3.0 #134 (@dlr-cjs)
- Update actions to use "ActionBuilder.onAndUse()" where possible #134 (@dlr-cjs)
- Update @Input definitions to state group optionality according to schema #134 (@dlr-cjs)
- Rename protobuf methods "add/get<X>Value" to "add/get<X>Values" #134 (@dlr-cjs)
- Update `getValueHigherEqual` and `getValueLowerEqual ` to `getValueLaterEqual` and `getValueEarlierEqual` #134 (@dlr-cjs)

## [3.1.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.1.0) - 2024-11-26
### Added
- Add new agent `HeatPumpTrader`' that aggregates heat pumps in buildings and arranges procurement at the day-ahead market #131 (@dlr_es, @dlr-cjs)

## [3.0.2](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.0.2) - 2024-10-31
### Fixed
- StorageTrader: Crash when using strategy MULTI_AGENT_SIMPLE #132 (@dlr-cjs, @dlr_es)

## [3.0.1](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.0.1) - 2024-10-14
### Fixed
- Configure artifacts to expire after one year #130 (@dlr_fn, @dlr-cjs)

## [3.0.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v3.0.0) - 2024-09-20
_If you are upgrading: please see [`UPGRADING.md`](UPGRADING.md)_
### Changed
- **Breaking**: Change `FuelType` input parameter from `enum` to `string_set` and move to `FuelsTrader` #120 (@dlr_fn, @dlr-cjs)
- **Breaking**: Change `Set` input parameter from `enum` to `string_set` and rename to `PolicySet` #5 (@dlr_fn, @dlr-cjs)
- **Breaking**: Change market zone inputs `OwnMarketZone` and `ConnectedMarketZone` to `string_set` and rename to `MarketZone` #123 (@dlr-cjs)
- **Breaking**: Change contract structure GateClosureInfo to be sent before forecasting #126 (@dlr-cjs, @dlr_fn)
- Replace `GreenHydrogenOperator` with `GreenHydrogenTrader` which allows marketing of (surplus) electricity #124 (@dlr-cjs, @dlr_jk)
- Change minimum requirement `fameio` version to 2.3.1 #5, #120 (@dlr-cjs)
- README: Update minimum Python version requirement to 3.9 !114, !130 (@dlr_fn, @dlr-cjs)

### Added
- add new agent `GreenHydrogenTraderMonthly` that has a PPA with a renewable operator to produce hydrogen with monthly equivalence #125 (@dlr-cjs, @dlr_jk)

### Removed
- **Breaking**: Deleted input Attribute `ElectricityForecastRequestOffsetInSeconds` from `StorageTrader` and `ElectrolysisTrader` #126 (@dlr-cjs, @dlr_fn)
- **Breaking**: Deleted input Attribute `HydrogenForecastRequestOffsetInSeconds` from `ElectrolysisTrader` #126 (@dlr-cjs, @dlr_fn)
- **Breaking**: Deleted input Attribute `ForecastRequestOffsetInSeconds` from `MeritOrderForecaster` and `PriceForecaster` #126 (@dlr-cjs, @dlr_fn)

### Fixed
- **Breaking**: Fixed typo in input attribute name `InvestmentExpensesInEURperMW` #114 (@dlr_fn, @dlr-cjs)
- Remove unnecessary warnings in `ArbitrageStrategist` #122 (@dlr_fn, @dlr-cjs)
- Javadoc publishing pipeline #129 (@dlr-cjs)

## [2.2.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v2.2.0) - 2024-05-28
### Changed
- Change Marginal and Bid messages as well as their forecast counterparts to have one message per agent #44 (@dlr-cjs)
- Change default logging level from `ERROR` to `WARN` #85 #110 (@dlr-cjs)
- Increase FAME-Core version to 1.7 #110 (@dlr-cjs)

### Added
- Add new agent `DayAheadMarketMultiZone` for market clearing with multiple coupled market zones #36 #109 (@dlr_elghazi, @dlr_fn, @dlr-cjs)
- Add new agent `MarketCoupling` which allows coupling of multiple `DayAheadMarketMultiZone` #36 (@dlr-cjs, @litotes18, @dlr_elghazi, @dlr_fn)
- Add new agent `ImportTrader` which can account for "negative" load from net-import hours #36 (@dlr-cjs, @dlr_elghazi, @dlr_fn)
- add new agent `GreenHydrogenOperator` that has a PPA with a renewable operator to produce hydrogen #103 (@dlr-cjs, @dlr_jk, @LeonardWilleke)
- add new Product `PpaInformation` to `VariableRenewableOperator` to directly sell power to a client #103 (@dlr_jk, @LeonardWilleke)
- Metadata added in schema.yaml #102 (@litotes18, @dlr-cjs)

### Fixed
- Fixed inconsistent behaviour for Storage operation at the end of simulation #110 (@dlr-cjs)

## [2.1.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v2.1.0) - 2024-04-04
### Changed
- Increased FAME-Core version to 1.6 #106 (@dlr-cjs, @dlr_fn)

## [2.0.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v2.0.0) - 2024-03-12
_If you are upgrading: please see [`UPGRADING.md`](UPGRADING.md)_

### Changed
- **Breaking**: Rename `EnergyExchange` to `DayAheadMarketSingleZone` #41 (@dlr-cjs)
- **Breaking**: Forecasting products now defined in class `Forecaster` instead of `MeritOrderForecaster` #53 (@dlr-cjs, @dlr_es, Seyerfarzad Sarfarazi, @kriniti)
- **Breaking**: FuelsMarket product `FuelsBill` renamed to `FuelBill` #54 (@dlr-cjs, @dlr_jk)
- **Breaking**: ConventionalPlantOperator now based on `FuelsTrader` interface using new `FuelBid` message #54 (@dlr-cjs, @dlr_jk)
- **Breaking**: StorageTrader input field `forecastRequestOffset` renamed to `electricityForecastRequestOffset` #54 (@dlr-cjs, @dlr_jk)
- **Breaking**: input field `DistributionMethod` moved to new group `Clearing` in DayAheadMarkets and MarketForecasters #86 (@dlr-cjs, @dlr_es, @dlr_elghazi, @litotes18)
- **Breaking**: Output field `CostsInEUR` in PowerPlantOperator & StorageTrader renamed to `VariableCostsInEUR` #54 (@dlr-cjs, @dlr_jk)
- **Breaking**: Renamed output fields to harmonise them across agent types #76 (@dlr-cjs)
  - `ConventionalPlantOperator`: rename column `DispatchedPowerInMWHperPlant` to `DispatchedEnergyInMWHperPlant`
  - `ConventionalTrader`: rename column `OfferedPowerInMW` to `OfferedEnergyInMWH`
  - `ConventionalTrader`: rename column `AwardedPower` to `AwardedEnergyInMWH`
  - `DayAheadMarket`: rename column `TotalAwardedPowerInMW` to `AwardedEnergyInMWH`
  - `MarketForecaster`: rename column `AwardedPowerForecast` to `AwardedEnergyForecastInMWH`
  - `MarketForecaster`: rename column `ElectricityPriceForecast` to `ElectricityPriceForecastInEURperMWH`
  - `PowerPlantOperator`: rename column `AwardedPowerInMWH` to `AwardedEnergyInMWH`
  - `PowerPlantOperator`: rename column `OfferedPowerInMW` to `OfferedEnergyInMWH`
  - `StorageTrader`: rename column `OfferedPowerInMW` to `OfferedEnergyInMWH`
  - `StorageTrader`: rename column `AwardedChargePowerInMWH` to `AwardedChargeEnergyInMWH`
  - `StorageTrader`: rename column `AwardedDischargePowerInMWH` to `AwardedDischargeEnergyInMWH`
  - `StorageTrader`: rename column `AwardedPowerInMWH` to `AwardedEnergyInMWH`
  - `AggregatorTrader`: rename column `TruePowerPotentialInMWH` to `TrueGenerationPotentialInMWH`
  - `SupportPolicy`: rename column `MarketValue` to `MarketValueInEURperMWH`
- **Breaking**: `AggregatorTrader`'s optional input field `Variance` renamed to `StandardDeviation` #91 (@dlr-cjs)
- **Breaking**: `DemandTrader` changed type of input for `ValueOfLostLoad` from `double` to `time_series` allowing time-dependent value of lost load #100 (@dlr-cjs, @dlr_jk, @dlr_es)
- OrderBookItems with negative power are not allowed #83 (@dlr-cjs, @dlr_elghazi)
- MeritOrderKernel throws exception if clearing fails #83 (@dlr-cjs, @dlr_elghazi)
- Introduced a new TraderWithClients class to remove errors related to not used actions #81 (@dlr-cjs, @dlr_elghazi)
- Changed default log level from FATAL to ERROR  #81 (@dlr-cjs, @dlr_elghazi)
- Forecaster now re-checks for missing forecasts in every hour #42 (@dlr-cjs)
- ArbitrageStrategist now extends `flexibility.Strategist` #54 (@dlr-cjs, @dlr_jk)
- Include accounting for storage self-discharge in storage strategies #21 (@dlr-cjs, @dlr_jk, @dlr_es, @dlr_elghazi)
- FileDispatcher(Storage) modified due to changes in `ArbitrageStrategist` #54 (@dlr-cjs, @dlr_jk)
- SystemCostMinimizer modified due to changes in `ArbitrageStrategist` #54 (@dlr-cjs, @dlr_jk)
- ArbitrageStrategist performs rounding of energy states and throws warning if not within tolerance #78 (@dlr_fn, @dlr-cjs, @dlr_elghazi)
- Newly raised error if in ConventionalPlantOperator no money foreseen for `Payout` is assigned to plants #89 (@litotes18, @dlr_jk, @dlr-cjs)
- StorageTrader now extends FlexibilityTrader #54 (@dlr-cjs, @dlr_jk)
- Refactoring of SupportPolicy, PolicyInfo, RenewableTrader and bidding strategies #66 (@dlr-cjs, @dlr_jk)
- Refactoring of StepPower #96 (@dlr-cjs)
- CITATION.cff harmonised with related JOSS paper #51 (@dlr-cjs)
- Update Reference examples to changes in outputs #41, #54, #75, #76 (@dlr-cjs)
- Move flexibility agents' forecasting to FlexibilityTrader #79 (@litotes18, @dlr_jk)
- Refactorings of `PowerPlant` to improve code clarity #94 (@dlr-cjs)
- CI: Any `javadoc` warning will now fail the pipeline #101 (@dlr-cjs)

### Added
- Package `electrolysis`: including `Electrolyzer` and related dispatch strategies #54 (@dlr-cjs, @dlr_jk)
- `ElectrolysisTrader`: new Trader demanding electricity and producing hydrogen from it via electrolysis #54 (@dlr-cjs, @dlr_jk)
- `FlexibilityTrader`: new abstract Trader operating a type of flexibility asset #54 (@dlr-cjs, @dlr_jk)
- `PriceForecaster`: new forecasting agent that provides forecasted electricity prices #53 (@dlr-cjs, @dlr_es, Seyedfarzad Sarfarazi, @kriniti)
- Package `storage.arbitrageStrategists`: added new strategist `ProfitMaximiser` #72 (@dlr-cjs)
- Package `storage.arbitrageStrategists`: added new strategist `MultiAgentSimple` #73 (@dlr-cjs)
- UrlModelService: utility class to support calling external models via POST web-requests #52 (@dlr-cjs)
- Package `flexibility`: Basic classes for flexibility's dispatch planning #54 (@dlr-cjs, @dlr_jk)
- Added new support instrument `FINANCIAL_CFD` in SupportPolicy and RenewableTrader #68 (@dlr-cjs, @litotes18, @dlr_jk)
- Package `accounting`: including class AnnualCostCalculator #54 (@dlr-cjs, @dlr_jk)
- New inputs and actions for `PowerPlantOperator`s to use AnnualCostCalculator #99 (@dlr-cjs)
- Added new option `ShortagePrice` to MarketForecaster and DayAheadMarket to dynamically adjust scarcity prices #86 (@dlr-cjs, @dlr_es, @dlr_elghazi, @litotes18)
- `FuelsMarket`: new Fuels `BIOMASS` and `OTHER` #54 (@dlr-cjs, @dlr_jk)
- `FuelsTrader`: interface to trade with FuelsMarket #54 (@dlr-cjs, @dlr_jk)
- `FuelBid` message to send bids for fuels to FuelsMarket #54 (@dlr-cjs, @dlr_jk)
- `Trader`: has new product `AnnualCostReport` #54 (@dlr-cjs, @dlr_jk)
- `ElectrolysisTrader`: add optional bidding price limit override #57 (@dlr_jk, @dlr-cjs)
- `ConventionalOperator`: add output `ReceivedMoneyInEUR` for received money per plant #74 (@dlr_cjs)
- Package `forecast`: added `PriceForecasterFile` to provide price forecasts from file #95 (@dlr-cjs)
- FileDispatcher(Storage): added optional input parameter `DispatchTolerance` #50 (@dlr_jk, @litotes18, @dlr_fn)
- Added missing javadocs #101 (@dlr-cjs, @dlr_jk)
- JOSS Paper at folder 'paper/' #3 (@dlr-cjs, @dlr_fn, @litotes18, @dlr_jk, @kriniti, @kyleniemeyer)
- Tests in packages `accounting` and `util` #54 (@dlr-cjs, @dlr_jk)
- UPGRADING.md: help people with upgrading tasks #58 (@dlr-cjs)
- MarketClearing: Checks for Bid message integrity #54 (@dlr-cjs, @dlr_jk)
- CI: Automatic checks for changelog updates #60 (@dlr-cjs)
- README.md: added `Acknowledgements` section #70 (@dlr-cjs)
- README.md: added link to REMix !106 (@dlr-cjs)

### Removed
- **Breaking**: Drop support of JDK 8, 9 and 10; new minimum version is JDK 11, associated with #52 (@dlr-cjs)
- **Breaking**: Remove unused input parameter `PurchaseLeviesAndTaxesInEURperMWH` from `StorageTrader`

### Fixed
- PredefinedPlantBuilder: delivering portfolio for second year #65 (@dlr-cjs, @dlr_jk, @dlr_fn)
- Storage: bids could exceed market limits - now they abide by the market limits #47 (@dlr-cjs)
- AggregatorTrader: corrected association of support payments in case of multiple clients with similar set #104 (@dlr-cjs)
- Storage: discretisation of planning no longer causes over-charging or under-depletion #55 (@dlr-cjs)
- Forecaster: wrote out forecast prices with one-hour lag - the time lag is removed #48 (@dlr-cjs)
- ConventionalPlantOperator: did not sort power plants for dispatch correctly - sorting now according to marginal cost #97 (@dlr-cjs)
- CI: Pipeline to publish javadocs #62 #63 (@dlr-cjs)
- ConventionalPlantOperator: do not store outputs for inactive plants #75 (@dlr_fn, @dlr-cjs)
- Paper: corrected year in reference Weidlich et al. (2008) #88 (@dlr_fn)
- Error messages in example `input.pb` file #87 (@dlr_jk, @dlr-cjs, @litotes18)

## [1.3](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v1.3) - 2023-03-21
### Changed
- StorageTrader with Strategist "DISPATCH_FILE" can now also provide forecasts
- REUSE licence statements for each file in repository
- README improved
- Use FAME-Core version 1.4.2
- Improved packaging with executable JAR file

### Added
- IndividualPlantBuilder: allows to parameterise individual conventional power plants from a list
- FuelsMarket: new fuel type "HYDROGEN"
- Automatic unit testing infrastructure
- Reference results for an integration test

### Fixed
- PlantBuildingManager: calculation of Portfolio targetTime fixed
- CfD: Bidding strategy in RenewableTrader was not sensible and thus updated
- Missing Marginals: Always send at least one supply Marginal message, even if total available power is Zero
- Typos in issue templates

## [1.2](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v1.2) - 2022-02-14
### Changed
- Harmonised inputs & outputs across agents

## [1.1](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v1.1) - 2022-01-21
### Changed
- Harmonised contracting for Forecaster and Exchange

## [1.0](https://gitlab.com/dlr-ve/esy/amiris/amiris/-/releases/v1.0) - 2021-12-06
_Initial Release._
