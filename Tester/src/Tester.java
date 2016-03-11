import org.junit.Test;



public class Tester {
    @Test
    public void go()
    {
        Assert.assertEquals("Europe/Helsinki", TimezoneMapper.latLngToTimezoneString(65.012197, 25.471152));
        Assert.assertEquals("America/Chicago", TimezoneMapper.latLngToTimezoneString(41.8788764, -87.6359149));
        Assert.assertEquals("Europe/Paris", TimezoneMapper.latLngToTimezoneString(42.75676, -0.092723));
    }

}
