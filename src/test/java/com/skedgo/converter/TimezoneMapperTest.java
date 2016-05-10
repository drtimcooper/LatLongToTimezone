package com.skedgo.converter;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class TimezoneMapperTest {
    @Test
    public void go() {
        Assert.assertEquals(TimezoneMapper.latLngToTimezoneString(65.012197, 25.471152), "Europe/Helsinki");
        assertEquals(TimezoneMapper.latLngToTimezoneString(41.8788764, -87.6359149), "America/Chicago");
        assertEquals(TimezoneMapper.latLngToTimezoneString(42.75676, -0.092723), "Europe/Paris");
    }

}