package agents.markets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testUtils.Exceptions;

public class AllTransmissionCapacitiesTest {
	AllTransmissionCapacities capacities;

	@BeforeEach
	public void setUp() {
		capacities = new AllTransmissionCapacities();
	}

	@Test
	public void get_unregistered_returnsZero() {
		assertEquals(0, capacities.getRemainingCapacity(0L, 1L));
	}

	@Test
	public void register_get_returnsSetValue() {
		capacities.register(0L, 1L, 100);
		assertEquals(100, capacities.getRemainingCapacity(0L, 1L), 1E-10);
	}

	@Test
	public void register_getBackwards_returnsZero() {
		capacities.register(0L, 1L, 100);
		assertEquals(0, capacities.getRemainingCapacity(1L, 0L), 1E-10);
	}

	@Test
	public void register_addTransmission_get_returnsRemaining() {
		capacities.register(0L, 1L, 100);
		capacities.addTransmission(0L, 1L, 20);
		assertEquals(80, capacities.getRemainingCapacity(0L, 1L), 1E-10);
	}

	@Test
	public void register_addTransmission_getInverse_returnsAvailableRetransfer() {
		capacities.register(0L, 1L, 100);
		capacities.addTransmission(0L, 1L, 20);
		assertEquals(20, capacities.getRemainingCapacity(1L, 0L), 1E-10);
	}

	@Test
	public void register_addTransmission_getInverse_returnsAvailableRetransferPlusOwnCapacity() {
		capacities.register(0L, 1L, 100);
		capacities.register(1L, 0L, 100);
		capacities.addTransmission(0L, 1L, 20);
		assertEquals(120, capacities.getRemainingCapacity(1L, 0L), 1E-10);
	}

	@Test
	public void register_addTransmission_addInverseTransmission_returnsOriginalCapacities() {
		capacities.register(1L, 0L, 200);
		capacities.register(0L, 1L, 100);
		capacities.addTransmission(0L, 1L, 20);
		capacities.addTransmission(1L, 0L, 20);
		assertEquals(100, capacities.getRemainingCapacity(0L, 1L), 1E-10);
		assertEquals(200, capacities.getRemainingCapacity(1L, 0L), 1E-10);
	}

	@Test
	public void register_targetEqualsSender_throws() {
		Exceptions.assertThrowsFatalMessage(AllTransmissionCapacities.ERR_ORIGIN_IS_TARGET,
				() -> capacities.register(0L, 0L, 42));
	}
	
	@Test
	public void clear_removesPreviousData() {
		capacities.register(1L, 0L, 200);
		capacities.register(0L, 1L, 100);
		capacities.addTransmission(0L, 1L, 20);
		
		capacities.clear();
		assertEquals(0, capacities.getRemainingCapacity(0L, 1L), 1E-10);
		assertEquals(0, capacities.getRemainingCapacity(1L, 0L), 1E-10);		
	}
	
	
}
