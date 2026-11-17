// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package communications.portable;

import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.SupplyOrderBook;
import agents.markets.meritOrder.books.TransferOrderBook;
import agents.markets.meritOrder.books.TransmissionBook;
import communications.message.TransmissionCapacity;
import de.dlr.gitlab.fame.communication.transfer.ComponentCollector;
import de.dlr.gitlab.fame.communication.transfer.ComponentProvider;
import de.dlr.gitlab.fame.communication.transfer.Portable;
import de.dlr.gitlab.fame.time.TimeStamp;

/** Specifies the data that EnergyExchange agents have to send to the MarketCoupling agent in order to minimise price variance
 * across markets. The same data type is return from the MarketCoupling agent to the registered EnergyExchange(s).
 * 
 * @author A. Achraf El Ghazi, Felix Nitsch, Christoph Schimeczek */
public class CouplingData implements Portable, Cloneable {
	private TimeStamp clearingTime;
	private SupplyOrderBook supplyOrderBook;
	private DemandOrderBook demandOrderBook;
	private TransmissionBook transmissionBook;
	private TransferOrderBook importOrderBook;
	private TransferOrderBook exportOrderBook;

	/** required for {@link Portable}s */
	public CouplingData() {}

	/** Create a CouplingData object
	 * 
	 * @param clearingTime at which the supply, demand and transmissions are valid
	 * @param demandOrderBook of the demand bids
	 * @param supplyOrderBook of the supply bids
	 * @param transmissionBook of the transmission capacities */
	public CouplingData(TimeStamp clearingTime, DemandOrderBook demandOrderBook, SupplyOrderBook supplyOrderBook,
			TransmissionBook transmissionBook) {
		this.clearingTime = clearingTime;
		this.demandOrderBook = demandOrderBook;
		this.supplyOrderBook = supplyOrderBook;
		this.transmissionBook = transmissionBook;
		this.importOrderBook = new TransferOrderBook();
		this.exportOrderBook = new TransferOrderBook();
	}

	@Override
	public void addComponentsTo(ComponentCollector collector) {
		collector.storeComponents(clearingTime);
		collector.storeComponents(demandOrderBook);
		collector.storeComponents(supplyOrderBook);
		collector.storeComponents(transmissionBook);
		collector.storeComponents(importOrderBook);
		collector.storeComponents(exportOrderBook);
	}

	@Override
	public void populate(ComponentProvider provider) {
		clearingTime = provider.nextComponent(TimeStamp.class);
		demandOrderBook = provider.nextComponent(DemandOrderBook.class);
		supplyOrderBook = provider.nextComponent(SupplyOrderBook.class);
		transmissionBook = provider.nextComponent(TransmissionBook.class);
		importOrderBook = provider.nextComponent(TransferOrderBook.class);
		exportOrderBook = provider.nextComponent(TransferOrderBook.class);
	}

	/** @return time of market clearing this {@link CouplingData} is associated with */
	public TimeStamp getClearingTime() {
		return clearingTime;
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

	/** @return the transmissionBook of this object */
	public TransmissionBook getTransmissionBook() {
		return transmissionBook;
	}

	/** @return the transmission capacity amount from this market's region to the given target Region
	 * @param target Region */
	public double getTransmissionTo(String target) {
		for (TransmissionCapacity tc : transmissionBook.getTransmissionCapacities()) {
			if (tc.getTarget().equals(target)) {
				return tc.getTransferCapacityInMW();
			}
		}
		return 0;
	}

	/** @return the importOrderBook of this object */
	public TransferOrderBook getImportOrderBook() {
		return importOrderBook;
	}

	/** Updates the {@link CouplingData#importOrderBook} of this object with
	 * 
	 * @param transferOrderBook to update with */
	public void updateImportBook(TransferOrderBook transferOrderBook) {
		for (long traderId : transferOrderBook.getTraders()) {
			importOrderBook.addTraderBids(traderId, transferOrderBook.getBidsOf(traderId));
		}
	}

	/** @return the exportOrderBook of this object */
	public TransferOrderBook getExportOrderBook() {
		return exportOrderBook;
	}

	/** Updates the {@link CouplingData#exportOrderBook} of this object with
	 * 
	 * @param transferOrderBook to update with */
	public void updateExportBook(TransferOrderBook transferOrderBook) {
		for (long traderId : transferOrderBook.getTraders()) {
			exportOrderBook.addTraderBids(traderId, transferOrderBook.getBidsOf(traderId));
		}
	}

	/** @return a deep copy of CouplingRequest caller */
	public CouplingData clone() {
		CouplingData clone = new CouplingData();
		clone.clearingTime = clearingTime;
		clone.demandOrderBook = demandOrderBook.clone();
		clone.supplyOrderBook = supplyOrderBook.clone();
		clone.transmissionBook = transmissionBook.clone();
		clone.importOrderBook = importOrderBook.clone();
		clone.exportOrderBook = exportOrderBook.clone();
		return clone;
	}

	/** @return origin Region of this CouplingData */
	public String getOrigin() {
		return transmissionBook.getOrigin();
	}
}
