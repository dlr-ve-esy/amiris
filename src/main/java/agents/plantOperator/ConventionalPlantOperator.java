// SPDX-FileCopyrightText: 2021-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.plantOperator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import agents.conventionals.PlantBuildingManager;
import agents.conventionals.Portfolio;
import agents.conventionals.PowerPlant;
import agents.markets.CarbonMarket;
import agents.markets.FuelsMarket;
import agents.markets.FuelsTrader;
import agents.trader.TraderWithClients;
import communications.message.AmountAtTime;
import communications.message.ClearingTimes;
import communications.message.Co2Cost;
import communications.message.FuelBid;
import communications.message.FuelBid.BidType;
import communications.portable.MarginalsAtTime;
import communications.message.FuelCost;
import communications.message.FuelData;
import communications.message.PointInTime;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.communication.CommUtils;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.service.output.ComplexIndex;
import de.dlr.gitlab.fame.service.output.Output;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Operates a portfolio of conventional power plant units of same type, e.g. nuclear or hard-coal power plant unit. */
public class ConventionalPlantOperator extends PowerPlantOperator implements FuelsTrader {
	public static final double MUST_RUN_COST = Double.NEGATIVE_INFINITY;

	static final String ERR_MISSING_CO2_COST = "Missing at least one CO2 cost item to match corresponding fuel cost item(s).";
	static final String ERR_MISSING_FUEL_COST = "Missing at least one fuel cost item to match corresponding CO2 cost item(s).";
	static final String ERR_MISSING_POWER = " cannot fulfil dispatch due to missing power in MWh: ";
	static final String ERR_PAYOUT_VANISH = "ERROR: ConventionalPlants received money but were not dispatched! Ensure Payout contracts are scheduled after DispatchAssignment contracts for ";

	private static final double NUMERIC_TOLERANCE = 1E-10;

	/** Products of {@link ConventionalPlantOperator} */
	@Product
	public static enum Products {
		/** Total actual emissions produced during power generation */
		Co2Emissions,
		/** Request for a Co2 Price forecast at given time */
		Co2PriceForecastRequest,
		/** Request for a Co2 Price at given time */
		Co2PriceRequest,
	}

	@Output
	private static enum OutputFields {
		DispatchedEnergyInMWHperPlant, VariableCostsInEURperPlant, ReceivedMoneyInEURperPlant, Co2EmissionsInT,
		FuelConsumptionInThermalMWH
	}

	/** The list of all power plants to be operated (now and possibly power plants to become active in the near future) */
	private Portfolio portfolio;
	private FuelData myFuelData;
	private ArrayList<AmountAtTime> fuelConsumption = new ArrayList<>();
	private ArrayList<AmountAtTime> co2Emissions = new ArrayList<>();

	private HashMap<TimeStamp, Double> fuelPrice = new HashMap<>();
	private HashMap<TimeStamp, Double> co2Price = new HashMap<>();
	private double lastDispatchedTotalInMW = 0;

	private enum PlantsKey {
		ID
	}

	private static final ComplexIndex<PlantsKey> dispatch = ComplexIndex.build(
			OutputFields.DispatchedEnergyInMWHperPlant, PlantsKey.class);
	private static final ComplexIndex<PlantsKey> variableCosts = ComplexIndex.build(
			OutputFields.VariableCostsInEURperPlant, PlantsKey.class);
	private static final ComplexIndex<PlantsKey> money = ComplexIndex.build(OutputFields.ReceivedMoneyInEURperPlant,
			PlantsKey.class);

	/** Fuel and CO2 costs valid at the same time */
	private class CostPair {
		public double fuelPrice = Double.NaN;
		public double co2Price = Double.NaN;
	}

	/** Creates a {@link ConventionalPlantOperator}
	 * 
	 * @param dataProvider provides input from config */
	public ConventionalPlantOperator(DataProvider dataProvider) {
		super(dataProvider);

		call(this::updatePortfolio).onAndUse(PlantBuildingManager.Products.PowerPlantPortfolio);
		call(this::requestFuelPrice).on(FuelsTrader.Products.FuelPriceForecastRequest)
				.use(TraderWithClients.Products.ForecastRequestForward);
		call(this::requestCo2Price).on(Products.Co2PriceForecastRequest)
				.use(TraderWithClients.Products.ForecastRequestForward);
		call(this::sendSupplyMarginals).on(PowerPlantOperator.Products.MarginalCostForecast)
				.use(CarbonMarket.Products.Co2PriceForecast, FuelsMarket.Products.FuelPriceForecast);
		call(this::requestFuelPrice).on(FuelsTrader.Products.FuelPriceRequest)
				.use(TraderWithClients.Products.GateClosureForward);
		call(this::requestCo2Price).on(Products.Co2PriceRequest).use(TraderWithClients.Products.GateClosureForward);
		call(this::sendSupplyMarginals).on(PowerPlantOperator.Products.MarginalCost).use(CarbonMarket.Products.Co2Price,
				FuelsMarket.Products.FuelPrice);
		call(this::reportCo2Emissions).on(Products.Co2Emissions);
		call(this::reportFuelConsumption).on(FuelsTrader.Products.FuelBid);
	}

	/** updates {@link #portfolio} to match that received from {@link PlantBuildingManager} */
	private void updatePortfolio(ArrayList<Message> input, List<Contract> contracts) {
		Message message = CommUtils.getExactlyOneEntry(input);
		portfolio = message.getAllPortableItemsOfType(Portfolio.class).get(0);
		myFuelData = new FuelData(portfolio.getFuelType());
	}

	/** sends {@link FuelData} message to specify FuelType and clearing time(s) for fuel price request
	 * 
	 * @param input requested ClearingTimes from associated Trader
	 * @param contracts single contract with FuelsMarket */
	private void requestFuelPrice(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		Message message = CommUtils.getExactlyOneEntry(input);
		ClearingTimes requestedTimes = message.getDataItemOfType(ClearingTimes.class);
		sendFuelPriceRequest(contract, myFuelData, requestedTimes);
	}

	/** sends {@link PointInTime} message to specify time of delivery for CO2 price forecast request */
	private void requestCo2Price(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		Message message = CommUtils.getExactlyOneEntry(input);
		ClearingTimes requestedTimes = message.getDataItemOfType(ClearingTimes.class);
		fulfilNext(contract, requestedTimes);
	}

	/** sends {@link MarginalCost} to connected Trader using incoming {@link FuelCost} and {@link Co2Cost} for one or multiple
	 * {@link TimeStamp}s */
	private void sendSupplyMarginals(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		HashMap<TimeStamp, CostPair> costPairs = matchingFuelAndCo2CostItems(input);

		double totalPowerPotentialInMW = 0;
		for (Entry<TimeStamp, CostPair> entry : costPairs.entrySet()) {
			double totalPowerPotentialPerTimeStamp = calculateAndSubmitNonZeroMarginals(contract, entry.getValue(),
					entry.getKey());
			totalPowerPotentialInMW += totalPowerPotentialPerTimeStamp;
		}
		if (contract.getProduct() == PowerPlantOperator.Products.MarginalCost) {
			store(PowerPlantOperator.OutputFields.OfferedEnergyInMWH, totalPowerPotentialInMW);
		}
	}

	/** Match timeStamps of given co2Cost & fuelCost messages
	 * 
	 * @param input Messages containing each {@link FuelCost} or {@link Co2Cost} data
	 * @return Pairs of fuelPrice and co2Price valid at the same {@link TimeStamp} */
	private HashMap<TimeStamp, CostPair> matchingFuelAndCo2CostItems(ArrayList<Message> input) {
		HashMap<TimeStamp, CostPair> costPairs = new HashMap<>();
		for (Message fuelMessage : CommUtils.extractMessagesWith(input, FuelCost.class)) {
			FuelCost fuelCost = fuelMessage.getDataItemOfType(FuelCost.class);
			CostPair costPair = new CostPair();
			costPair.fuelPrice = fuelCost.amount;
			costPairs.put(fuelCost.validAt, costPair);
		}
		for (Message co2Message : input) {
			Co2Cost co2Cost = co2Message.getDataItemOfType(Co2Cost.class);
			CostPair costPair = costPairs.get(co2Cost.validAt);
			if (costPair == null) {
				throw new RuntimeException(ERR_MISSING_FUEL_COST);
			}
			costPair.co2Price = co2Cost.amount;
		}
		for (CostPair costPair : costPairs.values()) {
			if (Double.isNaN(costPair.co2Price)) {
				throw new RuntimeException(ERR_MISSING_CO2_COST);
			}
		}
		return costPairs;
	}

	/** Calculates all {@link MarginalCost} for a given {@link CostPair} (at a specific time), submits them to the given
	 * {@link Contract} and returns the sum of their powerPotentials */
	private double calculateAndSubmitNonZeroMarginals(Contract contract, CostPair costPair, TimeStamp targetTime) {
		MarginalsAtTime marginals = calcSupplyMarginals(targetTime, costPair);
		fulfilNext(contract, marginals);
		return marginals.getTotalPowerPotentialInMW();
	}

	/** @return marginal cost items valid for the given time using the given {@link CostPair} */
	private MarginalsAtTime calcSupplyMarginals(TimeStamp targetTime, CostPair costPair) {
		fuelPrice.put(targetTime, costPair.fuelPrice);
		co2Price.put(targetTime, costPair.co2Price);
		List<PowerPlant> powerPlants = portfolio.getPowerPlantList();
		ArrayList<Marginal> marginals = new ArrayList<>(powerPlants.size());
		double mustRunPowerInMW = 0;
		for (PowerPlant plant : powerPlants) {
			Marginal marginal = plant.calcMarginal(targetTime, costPair.fuelPrice, costPair.co2Price, true);
			mustRunPowerInMW += plant.getMustRunPowerInMW(targetTime);
			if (marginal.getPowerPotentialInMW() > 0) {
				marginals.add(marginal);
			}
		}
		marginals.add(new Marginal(mustRunPowerInMW, MUST_RUN_COST));
		return new MarginalsAtTime(getId(), targetTime, marginals);
	}

	@Override
	protected double dispatchPlants(double awardedEnergy, TimeStamp time) {
		DispatchResult dispatchResult = updatePowerPlantStatus(getMustRunEnergyInMWH(time), awardedEnergy, time);
		this.fuelConsumption.add(new AmountAtTime(time, dispatchResult.getFuelConsumptionInThermalMWH()));
		this.co2Emissions.add(new AmountAtTime(time, dispatchResult.getCo2EmissionsInTons()));
		store(OutputFields.Co2EmissionsInT, dispatchResult.getCo2EmissionsInTons());
		store(OutputFields.FuelConsumptionInThermalMWH, dispatchResult.getFuelConsumptionInThermalMWH());
		return dispatchResult.getVariableCostsInEUR();
	}

	/** Returns cumulated must-run energy for all power plants in portfolio */
	private double getMustRunEnergyInMWH(TimeStamp time) {
		double mustRunEnergyInMWH = 0;
		for (PowerPlant plant : portfolio.getPowerPlantList()) {
			mustRunEnergyInMWH += plant.getMustRunPowerInMW(time);
		}
		return mustRunEnergyInMWH;
	}

	/** Sets load level of plants in {@link #portfolio} to generated awarded energy at the given TimeStamp; Awarded energy is first
	 * distributed to must-run capacities, then to the remaining capacities of the power plant. In both cases, power plants with low
	 * marginal cost are served first. The load level of power plants' that are not assigned to produced is set to Zero. Accounts
	 * for all emissions, fuel consumption and variable costs of dispatch.
	 *
	 * @param remainingMustRunEnergyInMWH total required energy to fulfil must-run constraints
	 * @param totalAwardedEnergyInMWH total electric energy that is to be produced
	 * @param time to dispatch at
	 * @return {@link DispatchResult} showing emissions, fuel consumption and variable costs */
	private DispatchResult updatePowerPlantStatus(double remainingMustRunEnergyInMWH, double totalAwardedEnergyInMWH,
			TimeStamp time) {
		remainingMustRunEnergyInMWH = Math.min(totalAwardedEnergyInMWH, remainingMustRunEnergyInMWH);

		lastDispatchedTotalInMW = totalAwardedEnergyInMWH;
		double currentFuelPrice = fuelPrice.remove(time);
		double currentCo2Price = co2Price.remove(time);
		DispatchResult dispatchTotal = new DispatchResult();
		double remainingAdditionalEnergyInMWH = Math.max(0, totalAwardedEnergyInMWH - remainingMustRunEnergyInMWH);

		List<PowerPlant> orderedPlantList = getSortedPowerPlantList(time, currentFuelPrice, currentCo2Price);
		ListIterator<PowerPlant> iterator = orderedPlantList.listIterator(orderedPlantList.size());
		while (iterator.hasPrevious()) {
			PowerPlant powerPlant = iterator.previous();
			double dispatchedMustRunPower = 0;
			double dispatchedAdditionalPower = 0;
			double availablePowerInMW = powerPlant.getAvailablePowerInMW(time);
			double requiredMustRunPowerInMW = powerPlant.getMustRunPowerInMW(time);
			if (remainingMustRunEnergyInMWH > 0 && requiredMustRunPowerInMW > 0) {
				dispatchedMustRunPower = Math.min(remainingMustRunEnergyInMWH, requiredMustRunPowerInMW);
				remainingMustRunEnergyInMWH -= dispatchedMustRunPower;
			}
			double remainingPowerPlantCapacity = availablePowerInMW - dispatchedMustRunPower;
			if (remainingAdditionalEnergyInMWH > 0 && remainingPowerPlantCapacity > 0) {
				dispatchedAdditionalPower = Math.min(remainingAdditionalEnergyInMWH, remainingPowerPlantCapacity);
				remainingAdditionalEnergyInMWH -= dispatchedAdditionalPower;
			}
			double totalPowerPlantPower = dispatchedMustRunPower + dispatchedAdditionalPower;
			if (totalPowerPlantPower > 0) {
				store(dispatch.key(PlantsKey.ID, powerPlant.getId()), totalPowerPlantPower);
			}
			DispatchResult plantDispatch = powerPlant.updateGeneration(time, totalPowerPlantPower, currentFuelPrice,
					currentCo2Price);
			if (plantDispatch.getVariableCostsInEUR() > 0) {
				store(variableCosts.key(PlantsKey.ID, powerPlant.getId()), plantDispatch.getVariableCostsInEUR());
			}
			dispatchTotal.add(plantDispatch);
		}
		if (remainingAdditionalEnergyInMWH > NUMERIC_TOLERANCE) {
			logger.error(this + ERR_MISSING_POWER + remainingAdditionalEnergyInMWH);
		}
		return dispatchTotal;
	}

	/** Returns a list of power plants sorted by marginal costs, descending */
	private List<PowerPlant> getSortedPowerPlantList(TimeStamp time, double fuelPrice, double co2Price) {
		List<PowerPlant> plants = new ArrayList<>(portfolio.getPowerPlantList());
		plants.sort(Comparator.comparing(p -> -p.calcMarginalCost(time, fuelPrice, co2Price)));
		return plants;
	}

	/** send co2 emissions caused by power generation to single contract receiver */
	private void reportCo2Emissions(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		for (AmountAtTime co2DataItem : co2Emissions) {
			fulfilNext(contract, co2DataItem);
		}
		co2Emissions.clear();
	}

	/** send thermal fuel consumption from power generation to single contract receiver */
	private void reportFuelConsumption(ArrayList<Message> input, List<Contract> contracts) {
		Contract contract = CommUtils.getExactlyOneEntry(contracts);
		for (AmountAtTime fuelDataItem : fuelConsumption) {
			FuelBid fuelBid = new FuelBid(fuelDataItem.validAt, fuelDataItem.amount, BidType.Demand, myFuelData.fuelType);
			sendFuelBid(contract, fuelBid);
		}
		fuelConsumption.clear();
	}

	@Override
	protected void digestPaymentPerPlant(TimeStamp dispatchTime, double totalPaymentInEUR) {
		double actualPaymentTotalInEUR = 0;
		for (PowerPlant plant : portfolio.getPowerPlantList()) {
			double shareOfLastDispatch = plant.getCurrentPowerOutputInMW() / lastDispatchedTotalInMW;
			double plantPaymentInEUR = totalPaymentInEUR * shareOfLastDispatch;
			if (Math.abs(plantPaymentInEUR) > 1E-10) {
				actualPaymentTotalInEUR += plantPaymentInEUR;
				store(money.key(PlantsKey.ID, plant.getId()), plantPaymentInEUR);
			}
		}
		if (Math.abs(actualPaymentTotalInEUR - totalPaymentInEUR) > 1) {
			throw new RuntimeException(ERR_PAYOUT_VANISH + this);
		}
	}

	@Override
	protected double getInstalledCapacityInMW() {
		return portfolio.getInstalledCapacityInMW(now());
	}

}