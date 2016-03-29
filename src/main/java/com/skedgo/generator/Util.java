package com.skedgo.generator;

import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.TimeZone;

/**
 * Created with IntelliJ IDEA.
 * User: Tim
 * Date: 28/02/15
 * Time: 12:13 AM
 * To change this template use File | Settings | File Templates.
 */
public class Util {
    @NotNull
    static final DecimalFormat[] FORMATTER = {
            new DecimalFormat("0"),
            new DecimalFormat("0.0"),
            new DecimalFormat("0.00"),
            new DecimalFormat("0.000"),
            new DecimalFormat("0.0000"),
            new DecimalFormat("0.00000"),
            new DecimalFormat("0.000000"),
            new DecimalFormat("0.0000000")
    };


    public static final double NO_NUM = -3e11;


    public static String formatNumber(double f, int decimals) {
        if (f == NO_NUM)
            return "?";
        synchronized (FORMATTER[decimals]) {
            return FORMATTER[decimals].format(f);
        }
    }

    /**
     * Useful for hashCode() and equals() fn's that compare stuff read from a text file.
     * Here we round an integer to the nearest cent and return the number of cents.
     */
    public static int round2(double d) {
        return (int) (d * 100 + 0.5);
    }

    /**
     * Useful for hashCode() and equals() fn's that compare stuff read from a text file.
     * Here we round an integer to the nearest 0.0001 and return the number of 0.0001's.
     */
    public static int round4(double d) {
        return (int)(10000 * (d + (d > 0 ? 0.00005 : -0.00005)));
    }

    public static int round6(double d) {
        return (int) (d * 1000000 + 0.5);
    }

    public static void stop()
    {
    }
}
