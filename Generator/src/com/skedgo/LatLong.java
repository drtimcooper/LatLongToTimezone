package com.skedgo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;


public class LatLong {
    public static final Pattern PATTERN =Pattern.compile("-?[0-9]{1,2}(.[0-9]+)?(,|%2C)-?[0-9]{1,3}.(.[0-9]+)?");
    public double lat, lng;

    @NotNull
    public static LatLong nullLatLong = new LatLong(0,0);


    public LatLong() { }

    public LatLong(double _lat, double _lng)
    {
        lat = _lat;
        lng = _lng;
    }

    public LatLong(LatLong other)
    {
        lat = other.lat;
        lng = other.lng;
    }


    /** The most common undefined LatLong's come from class Location, where they produce dummy
     * Location objects for storing the timezone. */
    public boolean isUndefined()
    {
        return (lat == 0 && lng == 0) || lat < -90 || lat > 90 || lng < -180 || lng > 180
                || Double.isNaN(lat) || Double.isNaN(lng);
    }

    public void toLatLongString(StringBuilder o, boolean useParenthesis)
    {
        if (useParenthesis) o.append('(');
        o.append(Util.formatNumber(lat,5));
        o.append(',');
        o.append(Util.formatNumber(lng,5));
        if (useParenthesis) o.append(')');
    }

    public String toLatLongString()
    {
        StringBuilder o = new StringBuilder();
        toLatLongString(o, true);
        return o.toString();
    }

    public String toString()
    {
        return toLatLongString();
    }

    public boolean equalsJustLatLong(Object o)
    {
        if (this == o)
            return true;
        if (!(o instanceof LatLong))
            return false;
        LatLong that = (LatLong)o;
        return Util.round4(that.lat) == Util.round4(lat) && Util.round4(that.lng) == Util.round4(lng);
    }

    public boolean equalsJustLatLong6Decimals(LatLong that)
    {
        return Util.round6(that.lat) == Util.round6(lat) && Util.round6(that.lng) == Util.round6(lng);
    }

    public int hashCodeJustLatLong()
    {
        return Util.round4(lat)*31 + Util.round4(lng);
    }

    /* These conversions are roughly appropriate for Sydney.
    public final double latToM = 110852;
    public final double lngToM = 93359;

    public double oldDistanceInMetres(@NotNull LatLong other)
    {
        double X = (this.lng - other.lng) * lngToM;
        double Y = (this.lat - other.lat) * latToM;
        return Math.sqrt(X*X + Y*Y);
    }*/

    public static final double EarthRadius = 6371000;
    static public final double radians = 3.14159/180;

    /** This is the Equirectangular approximation. It's a little slower than the Region.distanceInMetres()
     * formula. */
    public double distanceInMetres(@NotNull LatLong other)
    {
        double lngDelta = Math.abs(lng - other.lng);
        if (lngDelta > 180)
            lngDelta = 360 - lngDelta;
        double p1 = lngDelta * Math.cos(0.5*radians*(lat + other.lat));
        double p2 = (lat - other.lat);
        return EarthRadius * radians * Math.sqrt( p1*p1 + p2*p2);
    }

    public double distanceInDegreesSquared(LatLong other) {
        double latDiff = lat - other.lat;
        double lngDiff = lng - other.lng;
        return latDiff * latDiff + lngDiff * lngDiff;
    }

    /** This is the Equirectangular approximation. */
    public double distanceToLineSegment(LatLong A, LatLong B)
    {
        double Ax = (A.lng - lng) * Math.cos(0.5*radians*(A.lat + lat));
        double Ay = (A.lat - lat);
        double Bx = (B.lng - lng) * Math.cos(0.5*radians*(B.lat + lat));
        double By = (B.lat - lat);
        if (Ay == By) {
            if ((Ax < 0 && Bx < 0) || (Ax > 0 && Bx > 0))
                return Math.sqrt(Math.min(Ax*Ax+Ay*Ay, Bx*Bx+By*By)) * EarthRadius*radians;
            if (Ax == Bx)
                return Math.sqrt(Ax*Ax+Ay*Ay) * EarthRadius*radians;
        }
        else {
            double Ix = ((By - Ay) * (Ax*By - Ay*Bx)) / ((Ax-Bx)*(Ax-Bx) + (Ay-By)*(Ay-By));
            if ((Ix < Ax && Ix < Bx) || (Ix > Ax && Ix > Bx)) {
                return Math.sqrt(Math.min(Ax*Ax+Ay*Ay, Bx*Bx+By*By)) * EarthRadius*radians;
            }
        }
        return Math.abs((Bx-Ax)*Ay - Ax*(By-Ay)) / Math.sqrt((Bx-Ax)*(Bx-Ax) + (By-Ay)*(By-Ay)) * EarthRadius*radians;
    }

    /** Similar to above, except that you get a negative value if you're on the left side of this line segment. */
    public double signedDistanceToLineSegment(LatLong A, LatLong B)
    {
        double Ax = (A.lng - lng) * Math.cos(0.5*radians*(A.lat + lat));
        double Ay = (A.lat - lat);
        double Bx = (B.lng - lng) * Math.cos(0.5*radians*(B.lat + lat));
        double By = (B.lat - lat);
        double Ix = ((By - Ay) * (Ax*By - Ay*Bx)) / ((Ax-Bx)*(Ax-Bx) + (Ay-By)*(Ay-By));
        return ((Bx-Ax)*Ay - Ax*(By-Ay)) / Math.sqrt((Bx-Ax)*(Bx-Ax) + (By-Ay)*(By-Ay)) * EarthRadius*radians;
    }

    /**
     * Move 'this' in the direction of 'direction' by 'metres' metres. Note that this method will
     * have inaccuracies as you get close to the poles.
     * @return this for chaining
     */
    public LatLong moveTowards(LatLong direction, double metres)
    {
        double dX = direction.lng - lng;
        double dY = direction.lat - lat;
        double r = distanceInMetres(direction);
        if (r == 0)
            return this;
        double scale = metres / r;
        lng += dX*scale;
        lat += dY*scale;
        return this;
    }

    public String toString(boolean usingParenthesis) {
        StringBuilder o = new StringBuilder();
        toLatLongString(o, usingParenthesis);
        return o.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LatLong latLong = (LatLong) o;

        return Double.compare(latLong.lat, lat) == 0 && Double.compare(latLong.lng, lng) == 0;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(lat);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(lng);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    /**
     * Calculate counter-clockwise of three points p1, p2, p3
     * @return t < 0: turn right, t > 0: turn left, t ~ 0: go straight
     */
    public static double ccw(LatLong p1, LatLong p2, LatLong p3) {
        double a1, a2, b1, b2;
        a1 = p2.lat - p1.lat;
        b1 = p2.lng - p1.lng;
        a2 = p3.lat - p2.lat;
        b2 = p3.lng - p2.lng;
        return a1 * b2 - a2 * b1;
    }

    public static double angleABC(LatLong a, LatLong b, LatLong c) {
        double AB = a.distanceInMetres(b);
        double BC = b.distanceInMetres(c);
        double AC = a.distanceInMetres(c);
        if (AB == 0 || BC == 0) return 0;
        double cos = (AB * AB + BC * BC - AC * AC) / (2 * AB * BC);
        double angle = Math.acos(cos) * 180 / Math.PI;
        if (angle >= 180) angle -= 180;
        return angle;
    }
}
