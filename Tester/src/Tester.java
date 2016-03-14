import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Tester {
    @Test
    public void go()
    {
        assertEquals("Europe/Helsinki", TimezoneMapper.latLngToTimezoneString(65.012197, 25.471152));
        assertEquals("America/Chicago", TimezoneMapper.latLngToTimezoneString(41.8788764, -87.6359149));
        assertEquals("Europe/Paris", TimezoneMapper.latLngToTimezoneString(42.75676, -0.092723));
    }

}
