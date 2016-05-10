package com.skedgo.generator;


import java.util.List;


public interface BoundingBoxOrPolygon
{
    boolean contains(LatLong latLong);
    boolean contains(double lat, double lng);
    boolean intersects(BoundingBoxOrPolygon boundingPolygon);
    List<BoundingBoxOrPolygon> intersection(BoundingBoxOrPolygon boundingPolygon);
    BoundingBox getBoundingBox();
    Polygon asPolygon();
    void enlarge(int metres);
    List<LatLong> getLatLngs();
    boolean isEmpty();
}
