package communications.message;

import agents.markets.EnergyExchangeMulti.Region;
import agents.markets.meritOrder.Bid;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.books.TransferOrderBook;
import agents.markets.meritOrder.books.TransmissionBook;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;

/** Specifies the data that EnergyExchange agents have to send to the MarketCoupling agent in order to minimise price variance
 * across markets. The same data type is return from the MarketCoupling agent to the registered EnergyExchange(s).
 * 
 * @author A. Achraf El Ghazi, Felix Nitsch */
public class CouplingData implements Portable {
	private SupplyOrderBook supplyOrderBook;
	private DemandOrderBook demandOrderBook;
	private TransmissionBook transmissionBook;
	private TransferOrderBook importOrderBook;
	private TransferOrderBook exportOrderBook;

	/** required for {@link Portable}s */
	public CouplingData() {}

	/** Create a CouplingData object
	 * 
	 * @param demandOrderBook of the demand bids
	 * @param supplyOrderBook of the supply bids
	 * @param transmissionBook of the transmission capacities */
	public CouplingData(DemandOrderBook demandOrderBook, SupplyOrderBook supplyOrderBook,
			TransmissionBook transmissionBook) {
		this.demandOrderBook = demandOrderBook;
		this.supplyOrderBook = supplyOrderBook;
		this.transmissionBook = transmissionBook;
		this.importOrderBook = new TransferOrderBook();
		this.exportOrderBook = new TransferOrderBook();
	}

	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeComponents(demandOrderBook);
		collector.storeComponents(supplyOrderBook);
		collector.storeComponents(transmissionBook);
		collector.storeComponents(importOrderBook);
		collector.storeComponents(exportOrderBook);
	}

	@Override
	public void populate(ComponentProvider provider) {
		demandOrderBook = provider.nextComponent(DemandOrderBook.class);
		supplyOrderBook = provider.nextComponent(SupplyOrderBook.class);
		transmissionBook = provider.nextComponent(TransmissionBook.class);
		importOrderBook = provider.nextComponent(TransferOrderBook.class);
		exportOrderBook = provider.nextComponent(TransferOrderBook.class);
	}

	/** @return the demandOrderBook of this object */
	public DemandOrderBook getDemandOrderBook() {
		return demandOrderBook;
	}

	/** Sets {@link CouplingData#demandOrderBook} of this object with
	 * 
	 * @param demandOrderBook to set */
	public void setDemandOrderBook(DemandOrderBook demandOrderBook) {
		this.demandOrderBook = demandOrderBook;
	}

	/** @return the supplyOrderBook of this object */
	public SupplyOrderBook getSupplyOrderBook() {
		return supplyOrderBook;
	}

	/** Sets {@link CouplingData#supplyOrderBook} of this object with
	 * 
	 * @param supplyOrderBook to set */
	public void setSupplyOrderBook(SupplyOrderBook supplyOrderBook) {
		this.supplyOrderBook = supplyOrderBook;
	}

	/** @return the transmissionBook of this object */
	public TransmissionBook getTransmissionBook() {
		return transmissionBook;
	}

	/** @return the transmission capacity amount from this market's region to the given target Region
	 * @param target Region */
	public double getTransmissionTo(Region target) {
		for (TransmissionCapacity tc : transmissionBook.getTransmissionCapacities()) {
			if (tc.getTarget() == target) {
				return tc.getAmount();
			}
		}
		return 0;
	}

	/** Sets {@link CouplingData#transmissionBook} of this object with
	 * 
	 * @param transmissionBook to set */
	public void setTransmissionBook(TransmissionBook transmissionBook) {
		this.transmissionBook = transmissionBook;
	}

	/** updates {@link CouplingData#transmissionBook} of this object with the given amount for the given Region
	 * 
	 * @param region to update
	 * @param amount to update with */
	public void updateTransmissionBook(Region region, double amount) {
		for (TransmissionCapacity tc : transmissionBook.getTransmissionCapacities()) {
			if (tc.getTarget() == region) {
				tc.setAmount(amount);
			}
		}
	}

	/** @return the importOrderBook of this object */
	public TransferOrderBook getImportOrderBook() {
		return importOrderBook;
	}

	/** Sets {@link CouplingData#importOrderBook} of this object
	 * 
	 * @param importOrderBook to set */
	public void setImportOrderBook(TransferOrderBook importOrderBook) {
		this.importOrderBook = importOrderBook;
	}

	/** Updates the {@link CouplingData#importOrderBook} of this object with
	 * 
	 * @param transferOrderBook to update with */
	public void updateImportBook(TransferOrderBook transferOrderBook) {
		for (Bid bid : transferOrderBook.getBids()) {
			this.importOrderBook.addBid(bid);
		}
	}

	/** @return the exportOrderBook of this object */
	public TransferOrderBook getExportOrderBook() {
		return exportOrderBook;
	}

	/** Sets {@link CouplingData#exportOrderBook} of this object with
	 * 
	 * @param exportOrderBook to set */
	public void setExportOrderBook(TransferOrderBook exportOrderBook) {
		this.exportOrderBook = exportOrderBook;
	}

	/** Updates the {@link CouplingData#exportOrderBook} of this object with
	 * 
	 * @param transferOrderBook to update with */
	public void updateExportBook(TransferOrderBook transferOrderBook) {
		for (Bid bid : transferOrderBook.getBids()) {
			this.exportOrderBook.addBid(bid);
		}
	}

	/** @return a deep copy of CouplingRequest caller */
	public CouplingData clone() {
		CouplingData clone = new CouplingData();
		clone.setDemandOrderBook(demandOrderBook.clone());
		clone.setSupplyOrderBook(supplyOrderBook.clone());
		clone.setTransmissionBook(transmissionBook.clone());
		clone.setImportOrderBook(importOrderBook.clone());
		clone.setExportOrderBook(exportOrderBook.clone());
		return clone;
	}

	/** @return origin Region of this CouplingData */
	public Region getOrigin() {
		return transmissionBook.getOrigin();
	}
}
