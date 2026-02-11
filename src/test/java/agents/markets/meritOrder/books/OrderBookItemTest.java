// SPDX-FileCopyrightText: 2024-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package agents.markets.meritOrder.books;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static testUtils.Exceptions.assertThrowsMessage;
import org.junit.jupiter.api.Test;
import agents.markets.meritOrder.Bid;

public class OrderBookItemTest {

	@Test
	public void constructor_bidWithNegativePower_throws() {
		mock(Bid.class);
		Bid mockedBid = mock(Bid.class);
		when(mockedBid.getEnergyAmountInMWH()).thenReturn(-1.0);
		assertThrowsMessage(RuntimeException.class, OrderBookItem.ERR_NEGATIVE_POWER,
				() -> new OrderBookItem(mockedBid, 0L));
	}
}
