package com.skedgo.generator;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BoundingBox implements BoundingBoxOrPolygon
{
    public double lat1,lat2,lng1,lng2;

    public static BoundingBox createFromLocation(LatLong latLong, int distanceInMetres) {
        BoundingBox result = new BoundingBox(latLong.lat, latLong.lat, latLong.lng, latLong.lng);
        if (distanceInMetres == 0) distanceInMetres = 2000;
        result.enlargeByMetres(distanceInMetres);
        return result;
    }

    public BoundingBox(double lat1, double lat2, double lng1, double lng2)
    {
        if (lat1 < lat2) {
            this.lat1 = lat1;
            this.lat2 = lat2;
        }
        else {
            this.lat1 = lat2;
            this.lat2 = lat1;
        }
        if (lng1 < lng2) {
            this.lng1 = lng1;
            this.lng2 = lng2;
        }
        else {
            this.lng1 = lng2;
            this.lng2 = lng1;
        }
    }

    /** This constructor gives you either an empty bounding box,
     * or a box referring to nothing, suitable for using with 'extendToFit()'. */
    public BoundingBox(boolean everything)
    {
        if (everything) {
            lat1 = -999999;
            lat2 = 999999;
            lng1 = -999999;
            lng2 = 999999;
        }
        else {
            lat1 = 999999;
            lat2 = -999999;
            lng1 = 999999;
            lng2 = -999999;
        }
    }

    public BoundingBox(@NotNull BoundingBox other)
    {
        lat1 = other.lat1;
        lat2 = other.lat2;
        lng1 = other.lng1;
        lng2 = other.lng2;
    }

    public BoundingBox pad(final int metres) {
        // needs padding for cross-regional trips // https://redmine.buzzhives.com/issues/3130
        LatLong sw = new LatLong(lat1, lng1).moveTowards(new LatLong(lat1, -180), metres)
                .moveTowards(new LatLong(-90, 0), metres);
        LatLong ne = new LatLong(lat2, lng2).moveTowards(new LatLong(lat2, 180), metres)
                .moveTowards(new LatLong(90, 0), metres);
        return new BoundingBox(sw.lat, ne.lat, sw.lng, ne.lng);
    }

    public boolean contains(@NotNull LatLong latLng)
    {
        return contains(latLng.lat, latLng.lng);
    }

    public boolean contains(double lat, double lng)
    {
        double eps = 0.000001; // add a little bit as double comparison isn't exact
        return lat >= lat1 - eps && lat <= lat2 + eps
                && lng >= lng1 - eps && lng <= lng2 + eps;
    }

    public void extendToFit(@NotNull LatLong latLong)
    {
        if (latLong.lat < lat1)
            lat1 = latLong.lat;
        if (latLong.lat > lat2)
            lat2 = latLong.lat;
        if (latLong.lng < lng1)
            lng1 = latLong.lng;
        if (latLong.lng > lng2)
            lng2 = latLong.lng;
    }

    public void extendToFit(@NotNull BoundingBox other)
    {
        if (other.lat1 < lat1)
            lat1 = other.lat1;
        if (other.lat2 > lat2)
            lat2 = other.lat2;
        if (other.lng1 < lng1)
            lng1 = other.lng1;
        if (other.lng2 > lng2)
            lng2 = other.lng2;
    }

    public boolean intersectsCircle(@NotNull LatLong centre, double radius)
    {
        if (contains(centre))
            return true;

        LatLong closePoint = new LatLong();
        double closeLat = Math.abs(centre.lat - lat1) < Math.abs(centre.lat - lat2) ? lat1 : lat2;
        double closeLng = Math.abs(centre.lng - lng1) < Math.abs(centre.lng - lng2) ? lng1 : lng2;

        // evaluate against closest corner
        closePoint.lat = closeLat;
        closePoint.lng = closeLng;
        if (centre.distanceInMetres(closePoint) <= radius) {
            return true;
        }

        // evaluate point with lat of circle and long of closest border
        closePoint.lat = centre.lat;
//        closePoint.lng = closeLng;
        if (centre.distanceInMetres(closePoint) <= radius && contains(closePoint)) {
            return true;
        }

        // evaluate point with long of circle and lat of closest border
        closePoint.lat = closeLat;
        closePoint.lng = centre.lng;
        return (centre.distanceInMetres(closePoint) <= radius && contains(closePoint));
    }

    public boolean intersects(BoundingBox box)
    {
        if (lat1 >= box.lat2)
            return false;
        if (lat2 <= box.lat1)
            return false;
        if (lng1 >= box.lng2)
            return false;
        if (lng2 <= box.lng1)
            return false;
        return true;
    }

    public void intersect(BoundingBox box)
    {
        if (box.lat1 > lat1)
            lat1 = box.lat1;
        if (box.lat2 < lat2)
            lat2 = box.lat2;
        if (box.lng1 > lng1)
            lng1 = box.lng1;
        if (box.lng2 < lng2)
            lng2 = box.lng2;
    }

    public String toString()
    {
        StringBuilder o = new StringBuilder();
        o.append('(');
        if (lat1 < -180)
            o.append("NthPole");
        else o.append(Util.formatNumber(lat1,4));
        o.append(',');
        if (lng1 < -180)
            o.append("West");
        else o.append(Util.formatNumber(lng1,4));
        o.append(") to (");
        if (lat2 > 180)
            o.append("SthPole");
        else o.append(Util.formatNumber(lat2,4));
        o.append(',');
        if (lng2 > 180)
            o.append("East");
        else o.append(Util.formatNumber(lng2,4));
        o.append(')');
        return o.toString();
    }

    public int height()
    {
        // This constant is valid for all locations on Earth, since lines of latitude are equally spaced.
        return (int)((lat2 - lat1) * 110852);
    }

    public LatLong center() {
        return new LatLong(((lat2-lat1)/2)+lat1, ((lng2-lng1)/2)+lng1);
    }

    /** e.g. scale=0.1 means increase the size by 10% on all sides. */
    public void enlarge(double scale)
    {
        double latMargin = scale * (lat2 - lat1);
        double lngMargin = scale * (lng2 - lng1);
        lat1 -= latMargin;
        lat2 += latMargin;
        lng1 -= lngMargin;
        lng2 += lngMargin;
    }

    /** e.g. scale=0.1 means increase the size by 10% on all sides. */
    public void enlargeByMetres(double metres)
    {
        double latMargin = metres / 110852.0;
        double lngMargin = metres / (110852.0*Math.abs(Math.cos(LatLong.radians*lat1)));
        lat1 -= latMargin;
        lat2 += latMargin;
        lng1 -= lngMargin;
        lng2 += lngMargin;
    }

    public List<BoundingBoxOrPolygon> intersection(BoundingBoxOrPolygon other)
    {
        if (other instanceof BoundingBox) {
            BoundingBox overlap = new BoundingBox(this);
            overlap.intersect((BoundingBox)other);
            if (overlap.isEmpty())
                return null;
            List<BoundingBoxOrPolygon> list = new ArrayList<>();
            list.add(overlap);
            return list;
        }
        else return intersection((Polygon)other);
    }

    public BoundingBoxOrPolygon intersection(BoundingBox other)
    {
        BoundingBox box = new BoundingBox(true);
        box.lat1 = Math.max(lat1, other.lat1);
        box.lng1 = Math.max(lng1, other.lng1);
        box.lat2 = Math.min(lat2, other.lat2);
        box.lng2 = Math.min(lng2, other.lng2);
        if (box.lat1 >= box.lat2 || box.lng1 >= box.lng2)
            return null;
        return box;
    }

    public List<BoundingBoxOrPolygon> intersection(Polygon other)
    {
        return other.intersection(this);
    }

    @Override
    public boolean intersects(BoundingBoxOrPolygon other)
    {
        List<BoundingBoxOrPolygon> overlap = other.intersection(this);
        return overlap != null && ! overlap.isEmpty();
    }

    @Override
    public boolean isEmpty()
    {
        return lat1 >= lat2 || lng1 >= lng2;
    }

    @Override public BoundingBox getBoundingBox()
    {
        return this;
    }

    private static final double latToM = 110852;         // This is true for all latitudes
    private double lngToM = 93359;                       // This is the default, for Sydney, but it's adjusted in 'onLoad()'.

    @Override public void enlarge(int metres)
    {
        lat1 -= latToM;
        lat2 += latToM;
        lng1 -= lngToM;
        lng2 += lngToM;
    }

    public boolean containsAllOf(BoundingBox other)
    {
        if (other.lat1 < lat1 - 1)
            return false;
        if (other.lat2 > lat2 + 1)
            return false;
        if (other.lng1 < lng1 - 1)
            return false;
        if (other.lng2 > lng2 + 1)
            return false;
        return true;
    }

    @Override
    public List<LatLong> getLatLngs() {
        List<LatLong> result = new ArrayList<>();
        result.add(new LatLong(lat1, lng1));
        result.add(new LatLong(lat1, lng2));
        result.add(new LatLong(lat2, lng2));
        result.add(new LatLong(lat2, lng1));
        return result;
    }

    public Polygon asPolygon()
    {
        Polygon polygon = new Polygon();
        polygon.addPoint(lat1,lng1);
        polygon.addPoint(lat2,lng1);
        polygon.addPoint(lat2,lng2);
        polygon.addPoint(lat1,lng2);
        return polygon;
    }
}
