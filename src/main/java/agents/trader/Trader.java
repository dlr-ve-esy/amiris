package agents.trader;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import agents.markets.EnergyExchange;
import communications.message.MarginalCost;
import de.dlr.gitlab.fame.agent.Agent;
import de.dlr.gitlab.fame.agent.input.DataProvider;
import de.dlr.gitlab.fame.communication.Contract;
import de.dlr.gitlab.fame.communication.Product;
import de.dlr.gitlab.fame.communication.message.Message;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Abstract base class for all traders with {@link EnergyExchange}
 *
 * @author Christoph Schimeczek */
public abstract class Trader extends Agent {
	static final String ERR_NO_CONTRACT_IN_LIST = "No contract existing for agent: ";

	@Product
	public static enum Products {
		Bids, Payout, DispatchAssignment, BidsForecast, MeritOrderForecastRequest, PriceForecastRequest
	};

	public Trader(DataProvider dataProvider) {
		super(dataProvider);
	}

	/** @return a Map of Messages with {@link MarginalCost} sorted by the {@link TimeStamp} they are valid at */
	protected TreeMap<TimeStamp, ArrayList<Message>> sortMarginalsByTimeStamp(ArrayList<Message> messages) {
		TreeMap<TimeStamp, ArrayList<Message>> marginalsByTimeStamp = new TreeMap<>();
		for (Message message : messages) {
			TimeStamp timeStamp = message.getDataItemOfType(MarginalCost.class).deliveryTime;
			ArrayList<Message> marginalCost = saveGet(marginalsByTimeStamp, timeStamp);
			marginalCost.add(message);
		}
		return marginalsByTimeStamp;
	}

	/** Ensures that the given TreeMap returns an ArrayList at given time; if no value is present at the given time, an empty array
	 * is returned
	 * 
	 * @param marginalsByTimeStamp time-indexed TreeMap to search for key
	 * @param timeStamp time at which the key is required
	 * @return either present value at given key or (if not present) a newly added empty array */
	protected ArrayList<Message> saveGet(TreeMap<TimeStamp, ArrayList<Message>> marginalsByTimeStamp,
			TimeStamp timeStamp) {
		if (!marginalsByTimeStamp.containsKey(timeStamp)) {
			marginalsByTimeStamp.put(timeStamp, new ArrayList<Message>());
		}
		return marginalsByTimeStamp.get(timeStamp);
	}

	/** @return List of {@link MarginalCost} extracted from given Messages in ascending order of their marginal cost */
	protected ArrayList<MarginalCost> getSortedMarginalList(ArrayList<Message> messages) {
		ArrayList<MarginalCost> marginals = new ArrayList<>();
		for (Message message : messages) {
			marginals.add(message.getDataItemOfType(MarginalCost.class));
		}
		marginals.sort(MarginalCost.byCostAscending);
		return marginals;
	}

	/** @return the matching contract for a given agentId from given list of {@link Contract}s */
	protected Contract getMatchingContract(List<Contract> contracts, long agentId) {
		for (Contract contract : contracts) {
			if (agentId == contract.getReceiverId()) {
				return contract;
			}
		}
		throw new RuntimeException(ERR_NO_CONTRACT_IN_LIST + agentId);
	}
}