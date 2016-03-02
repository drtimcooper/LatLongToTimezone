import org.junit.Test;



public class Tester {
    @Test
    public void go()
    {
        assert TimezoneMapper.latLngToTimezoneString(65.012197, 25.471152).equals("Europe/Helsinki");
        assert TimezoneMapper.latLngToTimezoneString(41.8788764, -87.6359149).equals("America/Chicago");
        assert TimezoneMapper.latLngToTimezoneString(42.75676, -0.092723).equals("Europe/Paris");
    }

}
