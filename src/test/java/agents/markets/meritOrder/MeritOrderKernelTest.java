// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static testUtils.Exceptions.assertThrowsMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;
import agents.markets.meritOrder.MeritOrderKernel.MeritOrderClearingException;
import agents.markets.meritOrder.books.DemandOrderBook;
import agents.markets.meritOrder.books.OrderBook;
import agents.markets.meritOrder.books.OrderBookItem;
import agents.markets.meritOrder.books.SupplyOrderBook;

public class MeritOrderKernelTest {

	@Test
	public void clearMarketSimple_zeroSupply_throws() {
		SupplyOrderBook supplyBook = mock(SupplyOrderBook.class);
		DemandOrderBook demandBook = mock(DemandOrderBook.class);
		ArrayList<OrderBookItem> supplyItems = mockBookItemsPower(0., 0., 0.);
		ArrayList<OrderBookItem> demandItems = mockBookItemsPower(1, 2, 3);
		when(supplyBook.getOrderBookItems()).thenReturn(supplyItems);
		when(demandBook.getOrderBookItems()).thenReturn(demandItems);
		assertThrowsMessage(MeritOrderClearingException.class, MeritOrderKernel.ERR_NON_POSITIVE_ORDER_BOOK,
				() -> MeritOrderKernel.clearMarketSimple(supplyBook, demandBook));
	}

	private ArrayList<OrderBookItem> mockBookItemsPower(double... powerValues) {
		ArrayList<OrderBookItem> orderBookItems = new ArrayList<>();
		double total = 0;
		for (double powerValue : powerValues) {
			OrderBookItem orderBookItem = mock(OrderBookItem.class);
			when(orderBookItem.getCumulatedPowerUpperValue()).thenReturn(total + powerValue);
			orderBookItems.add(orderBookItem);
			total += powerValue;
		}
		return orderBookItems;
	}

	@Test
	public void clearMarketSimple_zeroDemand_throws() {
		SupplyOrderBook supplyBook = mock(SupplyOrderBook.class);
		DemandOrderBook demandBook = mock(DemandOrderBook.class);
		ArrayList<OrderBookItem> supplyItems = mockBookItemsPower(1, 2, 3);
		ArrayList<OrderBookItem> demandItems = mockBookItemsPower(0, 0, 0);
		when(supplyBook.getOrderBookItems()).thenReturn(supplyItems);
		when(demandBook.getOrderBookItems()).thenReturn(demandItems);
		assertThrowsMessage(MeritOrderClearingException.class, MeritOrderKernel.ERR_NON_POSITIVE_ORDER_BOOK,
				() -> MeritOrderKernel.clearMarketSimple(supplyBook, demandBook));
	}

	@Test
	public void clearMarketSimple_noCutLessDemand_returnsVirtualCutSupplyPrice() throws MeritOrderClearingException {
		SupplyOrderBook supplyBook = mockOrderBook(SupplyOrderBook.class, new double[] {100}, new double[] {21});
		DemandOrderBook demandBook = mockOrderBook(DemandOrderBook.class, new double[] {50}, new double[] {3000});
		ClearingDetails result = MeritOrderKernel.clearMarketSimple(supplyBook, demandBook);
		assertExpectedResult(result, 21, 50);
	}

	/** @return mocked {@link OrderBook} of given type created from given power and price value pairs; add virtual bid */
	private <T extends OrderBook> T mockOrderBook(Class<T> type, double[] powers, double[] prices) {
		T orderBook = mock(type);
		List<Double> powersWithVirtualBid = new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(powers)));
		powersWithVirtualBid.add(0.0);

		List<Double> pricesWithVirtualBid = new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(prices)));
		pricesWithVirtualBid.add(type == SupplyOrderBook.class ? Double.MAX_VALUE : -Double.MAX_VALUE);

		ArrayList<OrderBookItem> supplyItems = mockBookItemsPowerAndPrice(powersWithVirtualBid, pricesWithVirtualBid);
		when(orderBook.getOrderBookItems()).thenReturn(supplyItems);
		return orderBook;
	}

	/** @return List of mocked {@link OrderBookItem}s created from given power and price value pairs */
	private ArrayList<OrderBookItem> mockBookItemsPowerAndPrice(List<Double> powers, List<Double> prices) {
		ArrayList<OrderBookItem> orderBookItems = new ArrayList<>();
		double total = 0;
		for (int index = 0; index < powers.size(); index++) {
			OrderBookItem orderBookItem = mock(OrderBookItem.class);
			when(orderBookItem.getCumulatedPowerUpperValue()).thenReturn(total + powers.get(index));
			when(orderBookItem.getOfferPrice()).thenReturn(prices.get(index));
			orderBookItems.add(orderBookItem);
			total += powers.get(index);
		}
		return orderBookItems;
	}

	/** Asserts that given clearing result is sufficiently close to expected values */
	private void assertExpectedResult(ClearingDetails result, double priceInEURperMWH, double tradedEnergyInMWH) {
		assertEquals(priceInEURperMWH, result.marketPriceInEURperMWH, 1E-10);
		assertEquals(tradedEnergyInMWH, result.tradedEnergyInMWH, 1E-10);
	}

	@Test
	public void clearMarketSimple_noCutMoreDemand_returnsVirtualCutDemandPrice() throws MeritOrderClearingException {
		SupplyOrderBook supplyBook = mockOrderBook(SupplyOrderBook.class, new double[] {100}, new double[] {21});
		DemandOrderBook demandBook = mockOrderBook(DemandOrderBook.class, new double[] {200}, new double[] {3000});
		ClearingDetails result = MeritOrderKernel.clearMarketSimple(supplyBook, demandBook);
		assertExpectedResult(result, 3000, 100);
	}

	/** Cut at Same Power - Case 1: Previous supply is lower than next demand; Next supply is lower than previous demand */
	@Test
	public void clearMarketSimple_CutAtSamePowerCase1_returnsAverageOfNextSupplyAndNextDemand()
			throws MeritOrderClearingException {
		SupplyOrderBook supplyBook = mockOrderBook(SupplyOrderBook.class, new double[] {100, 50}, new double[] {0, 80});
		DemandOrderBook demandBook = mockOrderBook(DemandOrderBook.class, new double[] {100, 20}, new double[] {100, 50});
		ClearingDetails result = MeritOrderKernel.clearMarketSimple(supplyBook, demandBook);
		assertExpectedResult(result, 65, 100);
	}

	/** Cut at Same Power - Case 2: Previous supply is higher than next demand; Next supply is lower than previous demand */
	@Test
	public void clearMarketSimple_CutAtSamePowerCase2_returnsAverageOfPreviousSupplyAndNextSupply()
			throws MeritOrderClearingException {
		SupplyOrderBook supplyBook = mockOrderBook(SupplyOrderBook.class, new double[] {100, 50}, new double[] {60, 80});
		DemandOrderBook demandBook = mockOrderBook(DemandOrderBook.class, new double[] {100, 20}, new double[] {100, 50});
		ClearingDetails result = MeritOrderKernel.clearMarketSimple(supplyBook, demandBook);
		assertExpectedResult(result, 70, 100);
	}

	/** Cut at Same Power - Case 3: Previous supply is higher than next demand; Next supply is higher than previous demand */
	@Test
	public void clearMarketSimple_CutAtSamePowerCase3_returnsAverageOfPreviousSupplyAndPreviousDemand()
			throws MeritOrderClearingException {
		SupplyOrderBook supplyBook = mockOrderBook(SupplyOrderBook.class, new double[] {100, 50}, new double[] {60, 150});
		DemandOrderBook demandBook = mockOrderBook(DemandOrderBook.class, new double[] {100, 20}, new double[] {100, 50});
		ClearingDetails result = MeritOrderKernel.clearMarketSimple(supplyBook, demandBook);
		assertExpectedResult(result, 80, 100);
	}

	/** Cut at Same Power - Case 4: Previous supply is lower than next demand; Next supply is higher than previous demand */
	@Test
	public void clearMarketSimple_CutAtSamePowerCase4_returnsAverageOfPreviousDemandAndNextDemand()
			throws MeritOrderClearingException {
		SupplyOrderBook supplyBook = mockOrderBook(SupplyOrderBook.class, new double[] {100, 50}, new double[] {0, 150});
		DemandOrderBook demandBook = mockOrderBook(DemandOrderBook.class, new double[] {100, 20}, new double[] {100, 50});
		ClearingDetails result = MeritOrderKernel.clearMarketSimple(supplyBook, demandBook);
		assertExpectedResult(result, 75, 100);
	}

	@Test
	public void clearMarketSimple_DemandIsCut_returnsDemandPrice() throws MeritOrderClearingException {
		SupplyOrderBook supplyBook = mockOrderBook(SupplyOrderBook.class, new double[] {120, 20}, new double[] {21, 100});
		DemandOrderBook demandBook = mockOrderBook(DemandOrderBook.class, new double[] {100, 50}, new double[] {3000, 50});
		ClearingDetails result = MeritOrderKernel.clearMarketSimple(supplyBook, demandBook);
		assertExpectedResult(result, 50, 120);
	}

	@Test
	public void clearMarketSimple_HorizontalOverlap_returns() throws MeritOrderClearingException {
		SupplyOrderBook supplyBook = mockOrderBook(SupplyOrderBook.class, new double[] {100, 30}, new double[] {21, 100});
		DemandOrderBook demandBook = mockOrderBook(DemandOrderBook.class, new double[] {100, 50}, new double[] {3000, 100});
		ClearingDetails result = MeritOrderKernel.clearMarketSimple(supplyBook, demandBook);
		assertExpectedResult(result, 100, 130);
	}
}
