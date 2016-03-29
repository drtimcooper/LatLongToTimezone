package com.skedgo.generator;

import java.io.DataInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;



/** All the polygon methods ignore the curvature of the earth. */
public class Polygon implements BoundingBoxOrPolygon
{
    /** The end-point should _not_ be repeated. So a triangle means points.size() == 3. */
    protected List<LatLong> points;

    public BoundingBox box;

    private static final double roundingMargin=1e-10;


    private enum wedgeEnum {
        inside,
        outside,
        alongIncoming,
        alongOutgoing
    }

    public Polygon()
    {
        points = new ArrayList<>();
        box = new BoundingBox(false);
    }

    public Polygon(Polygon other)
    {
        points = new ArrayList<>(other.points.size());
        for (LatLong pt : other.points)
            points.add(new LatLong(pt));
        box = new BoundingBox(other.box);
    }

    /**
     * @param latLongs comma-separated LatLongs (which are comma-separated Lat & Long)
     * @throws java.lang.NumberFormatException if latLongs failed to parse
     */
    public Polygon(String latLongs) throws NumberFormatException {
        points = new ArrayList<>();
        box = new BoundingBox(false);
        Parser parser = new Parser(latLongs);
        while (! parser.finished()) {
            parser.skipWhitespace();
            if (parser.peek() == '(')
                parser.skip('(');
            double lat = parser.getDouble();
            parser.skip(',');
            double lng = parser.getDouble();
            parser.skipWhitespace();
            if (parser.peek() == ')' || parser.peek() == ',')
                parser.skip();
            addPoint(lat,lng);
        }
        checkSize();
    }

    public Polygon(DataInputStream stream, long points) throws IOException {
        while (points-- > 0)
            addPointWithCheck(new LatLong(stream));
        checkSize();
    }

    private void checkSize() {
        if (this.points.size() < 3)
            throw new NumberFormatException("not enough points for a polygon");
    }

    private void addPointWithCheck(final LatLong latLong) {
        if (latLong.equals(LatLong.nullLatLong) || latLong.isUndefined())
            throw new NumberFormatException();
        addPoint(latLong);
    }

    public void addPoint(LatLong latLng)
    {
        points.add(latLng);
        box.extendToFit(latLng);
    }

    public void addPoint(double lat, double lng)
    {
        addPoint(new LatLong(lat, lng));
    }

    private void addPtWithEqualityCheck(LatLong pt)
    {
        if (points.isEmpty() || ! points.get(points.size()-1).equalsJustLatLong6Decimals(pt))
            points.add(pt);
    }

    public boolean contains(LatLong latLng)
    {
        return contains(latLng.lat, latLng.lng);
    }

    public boolean contains(double testy, double testx)
    {
        if (! box.contains(testy, testx))
            return false;
        boolean inside = false;
        int nVert = points.size();
        LatLong last = points.get(nVert-1);
        double xj = last.lng;
        double yj = last.lat;
        for (int i = 0; i < nVert; i++) {
            double yi = points.get(i).lat;
            double xi = points.get(i).lng;
            if ( ((yi>testy) != (yj>testy)) && (testx < (xj-xi) * (testy-yi) / (yj-yi) + xi - 0.0001))
                inside = !inside;
            xj = xi;
            yj = yi;
        }
        return inside;
    }

    /** Is there any part of this polygon which is inside this bounding box? */
    public boolean intersects(BoundingBox box)
    {
        // Shortcut:
        if (! box.intersects(this.box))
            return false;

        // Either the box is inside the polygon,
        // or the polygon is inside the box,
        // or one of the 4 edges of the polygon would have to intersect the polygon.
        if (contains(box.lat1, box.lng1))
            return true;
        if (box.contains(points.get(0)))
            return true;

        //
        if (segmentIntersects(box.lat1, box.lng1, box.lat1, box.lng2))
            return true;
        if (segmentIntersects(box.lat1, box.lng2, box.lat2, box.lng2))
            return true;
        if (segmentIntersects(box.lat2, box.lng2, box.lat2, box.lng1))
            return true;
        if (segmentIntersects(box.lat2, box.lng1, box.lat1, box.lng1))
            return true;
        return false;
    }

    /** Does any of 'this's line segments intersect the given line-segment? */
    public boolean segmentIntersects(double lat1, double lng1, double lat2, double lng2)
    {
        int nVert = points.size();
        LatLong last = points.get(nVert-1);
        double xj = last.lng;
        double yj = last.lat;
        for (int i = 0; i < nVert; i++) {
            double yi = points.get(i).lat;
            double xi = points.get(i).lng;
            if (segmentIntersects(xj,yj,xi,yi, lng1,lat1,lng2,lat2))
                return true;
            xj = xi;
            yj = yi;
        }
        return false;
    }

    /** This is the static "Does AB intersect CD?" fn. */
    public static boolean segmentIntersects(double Ax, double Ay, double Bx, double By, double Cx, double Cy, double Dx, double Dy)
    {
        double s1_x = Bx - Ax;
        double s1_y = By - Ay;
        double s2_x = Dx - Cx;
        double s2_y = Dy - Cy;

        double s = (-s1_y * (Ax - Cx) + s1_x * (Ay - Cy)) / (-s2_x * s1_y + s1_x * s2_y);
        double t = ( s2_x * (Ay - Cy) - s2_y * (Ax - Cx)) / (-s2_x * s1_y + s1_x * s2_y);

        return (s >= 0 && s <= 1 && t >= 0 && t <= 1);
    }

    /** Make sure there are no duplicate points. */
    public void cleanUp()
    {
        LatLong prev = points.get(points.size()-1);
        for (int i=0; i < points.size(); i++) {
            LatLong pt = points.get(i);
            if (pt.equals(prev)) {
                points.remove(i);
                i--;
            }
            else prev = pt;
        }
    }

    public void ensureIsClockwise()
    {
        if (! isClockwise()) {
            Collections.reverse(points);
        }
    }

    public boolean isClockwise()
    {
        // Pick a random point which is on the boundary of the bounding box:
        double minLat = Double.MAX_VALUE;
        int minI=0;
        int n = points.size();
        for (int i=0; i < n; i++) {
            LatLong pt = points.get(i);
            if (pt.lat < minLat) {
                minLat = pt.lat;
                minI = i;
            }
        }
        int prev = minI - 1;
        do {
            if (prev < 0)
                prev = n - 1;
            if (points.get(prev).lat == minLat)
                prev--;
            else break;
        } while (prev != minI);
        minI = (prev == n - 1) ? 0 : prev + 1;

        // Is this wedge convex or concave, under the assumption of clockwiseness?
        // Should be convex.
        LatLong A = points.get(minI<=0?n-1:minI-1);
        LatLong B = points.get(minI);
        LatLong C = points.get(minI+1>=n?0:minI+1);
        return whichSide(A,B, C) > 0;
    }

    public boolean check()
    {
        int n = points.size();
        if (n <= 2)
            return false;
        //if (! isClockwise())
        //    return false;
        if (points.get(n-1).equalsJustLatLong6Decimals(points.get(n-2)))
            return false;
        return true;
    }

    public boolean hasSelfIntersection()
    {
        // This is a very inefficient implementation.
        LatLong last = points.get(points.size()-1);
        LatLong prev = last;
        for (LatLong pt : points) {
            LatLong prev2 = last;
            for (LatLong pt2 : points) {
                if (prev2 == prev || prev2 == pt || pt2 == prev || pt2 == pt)
                    ;
                else {
                    LatLong X = segmentIntersectPointWorker(prev,pt, prev2,pt2);
                    if (X != null)
                        return true;
                }
                prev2 = pt;
            }
            prev = pt;
        }
        return false;
    }

    public static class Intersection extends LatLong {
        public Segment segA, segB;
        Intersection nextA, nextB;
        public double f;// distance from A
        public wedgeEnum segAgoes, segBgoes;

        public Intersection(double lat, double lng)
        {
            super(lat,lng);
        }

        public Intersection(LatLong latLong)
        {
            super(latLong.lat, latLong.lng);
        }

        public String toString()
        {
            String s = super.toString();
            return s;
        }

        public boolean equals(Object obj)
        {
            if (! (obj instanceof LatLong))
                return false;
            LatLong latLng = (LatLong)obj;
            return latLng.lat == lat && latLng.lng == lng;
        }

        public boolean equalsExceptForRounding(LatLong other)
        {
            double dx = other.lng - lng;
            if (dx < -roundingMargin || dx > roundingMargin)
                return false;
            double dy = other.lat - lat;
            if (dy < -roundingMargin || dy > roundingMargin)
                return false;
            return true;
        }

        public boolean isRing(boolean ringA) {
            Intersection start = ringA ? nextA : nextB;
            Intersection X = start;
            int limit = 50000;
            do {
                X = ringA ? X.nextA : X.nextB;
                assert X != null;
                if (--limit <= 0)
                    return false;
            } while (X != this);
            return true;
        }

        public boolean leadsTo(Intersection target, boolean ringA) {
            Intersection X = this;
            do {
                if (X == target)
                    return true;
                if (X == null)
                    return false;
                X = ringA ? X.nextA : X.nextB;
            } while (X != this);
            return false;

        }
    }

    public static Intersection segmentIntersectPoint(Intersection A, Intersection B, Intersection C, Intersection D)
    {
        Intersection X = segmentIntersectPointWorker(A,B,C,D);
        if (X == null)
            return null;
        if (X.equalsExceptForRounding(A))
            return A;
        if (X.equalsExceptForRounding(B))
            return B;
        if (X.equalsExceptForRounding(C))
            return C;
        if (X.equalsExceptForRounding(D))
            return D;
        return X;
    }

    public static Intersection segmentIntersectPointWorker(LatLong A, LatLong B, LatLong C, LatLong D)
    {
        double Ax = A.lng;
        double Ay = A.lat;
        double Bx = B.lng;
        double By = B.lat;
        double Cx = C.lng;
        double Cy = C.lat;
        double Dx = D.lng;
        double Dy = D.lat;
        double ba_x = Bx - Ax;
        double ba_y = By - Ay;
        double dc_x = Dx - Cx;
        double dc_y = Dy - Cy;

        double denom = ba_x * dc_y - dc_x * ba_y;
        if (denom == 0)
            return null; // Collinear

        double ac_x = Ax - Cx;
        double ac_y = Ay - Cy;
        double s_numer = ba_x * ac_y - ba_y * ac_x;
        double t_numer = dc_x * ac_y - dc_y * ac_x;
        if (denom < 0) {
            denom = -denom;
            s_numer = -s_numer;
            t_numer = -t_numer;
        }
        if (s_numer < 0 || t_numer < 0 || s_numer > denom || t_numer > denom)
            return null;    // no collision
        if (s_numer == 0)
            return new Intersection(Cy,Cx);
        if (t_numer == 0)
            return new Intersection(Ay,Ax);

        // Collision detected
        double t = t_numer / denom;
        return new Intersection(Ay + (t * ba_y), Ax + (t * ba_x));
    }

    public boolean intersects(Polygon other)
    {
        // Shortcut:
        if (! box.intersects(other.box))
            return false;

        // Either 'other' is inside 'this',
        // or 'this' is inside 'other',
        // or one of this's segments intersects one of other's segments.
        if (other.contains(points.get(0)))
            return true;
        if (contains(other.points.get(0)))
            return true;

        //
        int nVert = points.size();
        LatLong last = points.get(nVert-1);
        double xj = last.lng;
        double yj = last.lat;
        for (int i = 0; i < nVert; i++) {
            double yi = points.get(i).lat;
            double xi = points.get(i).lng;
            if (other.segmentIntersects(xj,yj,xi,yi))
                return true;
            xj = xi;
            yj = yi;
        }
        return false;
    }

    /** If the points are specified in a random sequence, and we want to turn that into a suitable
     * sequence (where the edges don't self-intersect and sharp pieces are rare) then call this
     * method. This method will produce a clockwise sequence.
     *      You're allowed to have concave parts of your polygon, but if there's another arrangement
     * where the concavity is put into a different place to what you intend, and it 'looks better',
     * then we'll take the better-looking version.
     *      If you have more than one concavity then you might get different results depending on
     * the sequence the points are in initially.  I think this is rare. If it's an issue then a
     * solution would be to evaluate all sequences i.e. the full factorial, but that'll get
     * ridiculously slow with many points.
     * */
    public void fixUpPointSequence()
    {
        List<LatLong> newPoints = new ArrayList<>(points.size());
        for (LatLong point : points) {
            if (newPoints.size() < 2)
                newPoints.add(point);
            else {
                List<LatLong> bestSequence = null;
                double bestScore = Double.MAX_VALUE;
                for (int i=1; i <= newPoints.size(); i++) {    // Without loss of generality, keep the first point first.
                    List<LatLong> candidate = new ArrayList<>(newPoints.size());
                    candidate.addAll(newPoints);
                    candidate.add(i, point);
                    double score = scoreSequence(candidate);
                    if (score < bestScore) {
                        bestSequence = candidate;
                        bestScore = score;
                    }
                }
                newPoints = bestSequence;
            }
        }
        points = newPoints;
    }

    /** How 'good' does this sequence of points look? The sequence must be clockwise and have
     * the least acute angles possible. */
    private static double scoreSequence(List<LatLong> pts)
    {
        LatLong prev = pts.get(pts.size()-1);
        LatLong prevprev = pts.get(pts.size()-2);
        double prevAngle = Math.atan2(prev.lat-prevprev.lat, prev.lng-prevprev.lng);
        double score = 0;
        for (LatLong pt : pts) {
            double angle = Math.atan2(pt.lat-prev.lat, pt.lng-prev.lng);
            double diff = prevAngle - angle;
            if (diff > Math.PI)
                diff -= Math.PI*2;
            else if (diff < -Math.PI)
                diff += Math.PI*2;
            if (diff < 0)
                score += 10;        // This is anti-clockwise or self-intersecting.
            else score += diff;
            prev = pt;
            prevAngle = angle;
        }
        return score;
    }

    public LatLong centroid() {
        return new LatLong((box.lat1+box.lat2)/2, (box.lng1+box.lng2)/2);
    }

    public String toString()
    {
        if (points.isEmpty())
            return "<empty>";
        StringBuilder o = new StringBuilder();
        for (LatLong pt : points) {
            if (o.length() > 0)
                o.append(", ");
            o.append(pt);
        }
        return o.toString();
    }

    public List<LatLong> getPoints() {
        return new ArrayList<>(points);
    }




    /*--------------------------- Intersection and Merging ----------------------------*/

    @Override
    public List<BoundingBoxOrPolygon> intersection(BoundingBoxOrPolygon other)
    {
        if (other instanceof Polygon)
            return intersection((Polygon) other);
        BoundingBox otherBox = (BoundingBox)other;
        if (! intersects(otherBox))
            return null;
        if (whollyContainedInside(otherBox)) {
            return singleton(this);
        }
        Polygon polygon = newPolygon();
        polygon.addPoint(otherBox.lat1,otherBox.lng1);
        polygon.addPoint(otherBox.lat2,otherBox.lng1);
        polygon.addPoint(otherBox.lat2,otherBox.lng2);
        polygon.addPoint(otherBox.lat1,otherBox.lng2);
        List<BoundingBoxOrPolygon> list = intersection(polygon);
        return list;
    }

    @Override
    public Polygon asPolygon() { return this; }

    public boolean whollyContainedInside(BoundingBox box)
    {
        for (LatLong pt : points)
            if (! box.contains(pt))
                return false;
        return true;
    }

    private List<BoundingBoxOrPolygon> singleton(Polygon polygon)
    {
        ArrayList<BoundingBoxOrPolygon> singleton = new ArrayList<>();
        singleton.add(polygon);
        return singleton;
    }

    public List<BoundingBoxOrPolygon> intersection(Polygon other) {
        try {
            return intersectionWorker(other);
        } catch (PolygonException e) {
            return null;
        }
    }

    public List<BoundingBoxOrPolygon> intersectionWorker(Polygon other) throws PolygonException {
        // Step 1: Create the rings
        Segment ringA = createRing(true);
        Segment ringB = other.createRing(false);

        // Step 2: Find intersections
        Segment[] arrayA = ringA.sortedArray();
        Segment[] arrayB = ringB.sortedArray();
        for (Segment segA : arrayA) {
            BoundingBox box = new BoundingBox(segA.src.lat, segA.dst.lat, segA.src.lng, segA.dst.lng);
            for (Segment segB : arrayB) {
                if (segB.minLat() > box.lat2)
                    break;
                if (Math.min(segB.src.lat, segB.dst.lat) > box.lat2)
                    continue;
                if (Math.max(segB.src.lat, segB.dst.lat) < box.lat1)
                    continue;
                if (Math.min(segB.src.lng, segB.dst.lng) > box.lng2)
                    continue;
                if (Math.max(segB.src.lng, segB.dst.lng) < box.lng1)
                    continue;
                Intersection X = segmentIntersectPoint(segA.src, segA.dst, segB.src, segB.dst);
                if (X == null)
                    continue;
                X.segA = segA;
                X.segB = segB;
                segA.list.add(X);
                segB.list.add(X);
                if (X != segB.src && X.equalsExceptForRounding(segB.src))
                    segB.src = segB.prevSeg.dst = X;
                if (X != segA.src && X.equalsExceptForRounding(segA.src))
                    segA.src = segA.prevSeg.dst = X;
            }
        }
        arrayA = ringA.sortedArray();
        arrayB = ringB.sortedArray();

        // Step 3: Put intersections into sequence
        for (Segment segA : arrayA) {
            segA.sequence(true);
        }
        assert ringA.nextSeg.src.isRing(true);
        for (Segment segB : arrayB)
            segB.sequence(false);
        arrayA = ringA.sortedArray();
        arrayB = ringB.sortedArray();

        // Step 4: Find who is inside and who is outside
        Intersection rootA = arrayA[0].src;
        Intersection rootB = arrayB[0].src;
        Intersection A = rootA;
        Intersection B = A.nextA;
        Intersection C = B.nextA;
        assert rootA.isRing(true);
        do {
            if (B.nextB == null) {
                B.segBgoes = wedgeEnum.outside;
                B.segAgoes = wedgeEnum.inside;
            }
            else {
                B.segBgoes = (B.nextB == C) ? wedgeEnum.alongOutgoing : whichSideOfWedge(A,B,C, B.nextB);
            }
            A = B;
            B = C;
            C = B.nextA;
        } while (A != rootA);
        A = rootB;
        B = A.nextB;
        C = B.nextB;
        assert rootB.isRing(false);
        do {
            if (B.nextA == null) {
                B.segAgoes = wedgeEnum.outside;
                B.segBgoes = wedgeEnum.inside;
            }
            else {
                if (B.nextA == C)
                    B.segAgoes = wedgeEnum.alongOutgoing;
                else
                    B.segAgoes = whichSideOfWedge(A,B,C, B.nextA);
            }
            A = B;
            B = C;
            C = B.nextB;
        } while (A != rootB);

        // Step 5: Find a starting point
        Intersection start = findStartingPoint(rootA);
        if (start == null) {
            // There is no intersection.
            if (other.contains(internalPoint()))
                return singleton(this);
            else if (this.contains(other.internalPoint()))
                return singleton(other);
            else return null;
        }

        // Step 6: Do the topology
        ArrayList<BoundingBoxOrPolygon> list = new ArrayList<>();
        do {
            Polygon overlap = newPolygon();
            Intersection X = start;
            boolean followingA = true;
            do {
                int n = overlap.points.size()-1;
                if (n >= 1 && X.equalsExceptForRounding(overlap.points.get(n-1)))
                    overlap.points.remove(n);
                else if (overlap.points.size() >= 2 && colinear(overlap.points.get(n - 1), overlap.points.get(n), X))
                    overlap.points.set(n, new LatLong(X));
                else overlap.points.add(new LatLong(X));
                if (X.segAgoes == wedgeEnum.inside) {
                    if (X.segBgoes == wedgeEnum.outside || X.segBgoes == wedgeEnum.alongIncoming)
                        followingA = true;
                    else if (X.segBgoes == wedgeEnum.inside) {
                        followingA = ! followingA;  // in theory this gives us 2 polygons instead of a single self-intersecting polygon.
                    }
                    else throw new PolygonException();  // impossible results from 'whichSideOfWedge'
                }
                else if (X.segBgoes == wedgeEnum.inside) {
                    if (X.segAgoes == wedgeEnum.outside || X.segAgoes == wedgeEnum.alongIncoming)
                        followingA = false;
                    else assert false : "Impossible results from 'whichSideOfWedge'";
                }
                else if (X.segBgoes == wedgeEnum.alongOutgoing && X.segBgoes == wedgeEnum.alongOutgoing)
                    ;       // Continue along either
                else {
                    if (X.segBgoes == wedgeEnum.alongOutgoing || X.segBgoes == wedgeEnum.alongOutgoing)
                        assert false : "Impossible results from 'whichSideOfWedge'";
                    else assert false : "Shouldn't have been on this path.";
                }
                if (followingA)     // Ensure we don't use a segment twice.
                    X.segAgoes = wedgeEnum.outside;
                else X.segBgoes = wedgeEnum.outside;
                X = followingA ? X.nextA : X.nextB;
            } while (X != start);
            if (overlap.points.size() > 2) {
                overlap.calcBoundingBox();
                list.add(overlap);
            }
            start = findStartingPoint(rootA);
        } while (start != null);
        return list;
    }

    /** Are these 3 points colinear to withint about 100m? */
    private boolean colinear(LatLong A, LatLong B, Intersection C)
    {
        return (Math.abs(B.signedDistanceToLineSegment(A,C)) < 100);
    }

    /** Create a sample point which is definitely inside the polygon, not on the perimeter. */
    private LatLong internalPoint()
    {
        LatLong A = points.get(0);
        LatLong B = points.get(1);
        LatLong C = points.get(2);
        LatLong sample = new LatLong(B);
        sample.moveTowards(A,10);
        sample.moveTowards(C,10);
        return sample;
    }

    private Intersection findStartingPoint(Intersection rootA)
    {
        Intersection start = rootA;
        do {
            if (start.nextA != null && start.nextB != null
                    && (start.segAgoes == wedgeEnum.inside || start.segBgoes == wedgeEnum.inside))
                return start;
            start = start.nextA;
            if (start == rootA)
                return null;
        } while (true);
    }

    private Segment createRing(boolean ringA)
    {
        LatLong lastPt = points.get(points.size()-1);
        Intersection lastX = new Intersection(lastPt);
        Intersection prev = lastX;
        Segment prevSeg = null;
        Segment root = null;
        for (LatLong pt : points) {
            Intersection next = (pt == lastPt) ? lastX : new Intersection(pt);
            if (prev.equals(next))
                continue;   // Robustness, in case the input polygon repeats the end-point or otherwise has duplicates.
            Segment seg = new Segment(prev,next, ringA);
            if (prevSeg == null)
                root = seg;
            else
                prevSeg.nextSeg = seg;
            seg.prevSeg = prevSeg;
            prev = next;
            prevSeg = seg;
        }
        prevSeg.nextSeg = root;
        root.prevSeg = prevSeg;
        return root;
    }


    public class Segment {
        Intersection src,dst;
        Segment nextSeg, prevSeg;
        ArrayList<Intersection> list = new ArrayList<>();

        public Segment(Intersection _src, Intersection _dst, boolean ringA)
        {
            src = _src;
            dst = _dst;
            if (ringA) {
                src.segA = this;
                dst.segA = this;
                src.segB = null;
                dst.segB = null;
            }
            else {
                src.segA = null;
                dst.segA = null;
                src.segB = this;
                dst.segB = this;
            }
        }

        public Segment[] sortedArray()
        {
            List<Segment> A = new ArrayList<>();
            Segment segment=this;
            do {
                A.add(segment);
                segment = segment.nextSeg;
            } while (segment != this);
            Segment[] array = A.toArray(new Segment[0]);
            Arrays.sort(array, new Comparator<Segment>() {
                @Override
                public int compare(Segment seg1, Segment seg2) {
                    double c = seg1.minLat() - seg2.minLat();
                    if (c != 0)
                        return c > 0 ? 1 : -1;
                    return Double.compare(seg1.src.lng, seg2.src.lng);//for more deterministic behaviour.
                }
            });
            return array;
        }

        private double minLat()
        {
            return Math.min(src.lat, dst.lat);
        }

        /** Add 'src' and 'dst' to the set.
         * Sort all Intersections into sequence based on distance from src.
         * Remove duplicates.
         * Put in links between intersections. */
        public void sequence(boolean ringA)
        {
            for (Intersection X : this.list)
                X.f = X.distanceInDegreesSquared(src);
            Intersection[] array = list.toArray(new Intersection[0]);
            Arrays.sort(array, new Comparator<Intersection>() {
                @Override
                public int compare(Intersection X1, Intersection X2)
                {
                    return X1.f < X2 .f ? -1 : X1.f > X2.f ? 1 : 0;
                }
            });
            list.clear();
            list.add(src);
            Intersection prev = src;
            for (Intersection X : array) {
                if (X.equalsExceptForRounding(prev)) {
                    if (prev == src) {
                        src.segA = X.segA;
                        src.segB = X.segB;
                        if (src.nextA == null)
                            src.nextA = X.nextA;
                        if (src.nextB == null) {
                            src.nextB = X.nextB;
                        }
                    }
                }
                else {
                    list.add(X);
                    prev = X;
                }
            }
            if (prev.equalsExceptForRounding(dst)) {
                dst = unify(prev, dst);
                list.set(list.size() - 1, dst);
            }
            else list.add(dst);

            // Now the links:
            Intersection prevX = null;
            for (Intersection X : this.list) {
                if (prevX != null) {
                    assert prevX != dst;
                    if (ringA) {
                        prevX.nextA = X;
                    }
                    else {
                        prevX.nextB = X;
                    }
                }
                prevX = X;
            }
            assert dst != src;//Remove duplicate points before calling this, please.
            assert dst == nextSeg.src;
            assert src == prevSeg.dst;
            assert src == list.get(0) && dst == list.get(list.size()-1);
            assert src.leadsTo(nextSeg.src, ringA);
        }

        public String toString()
        {
            return src.toLatLongString() + " --> " + dst.toLatLongString();
        }

        public boolean eachIsRing(boolean ringA)
        {
            Segment segment=this;
            do {
                if (! segment.src.isRing(ringA))
                    return false;
                segment = segment.nextSeg;
            } while (segment != this);
            return true;
        }

        /** Replace references to 'F' with 'E'. */
        public void wasUnified(Intersection F, Intersection E)
        {
            if (src == F) {
                prevSeg.wasUnified(F, E);
                return;
            }
            if (dst == F)
                dst = nextSeg.src = E;
            if (F.segA == this || F.segA == nextSeg) {
                // doing A
                for (Intersection X = src; X != dst && X != null; X = X.nextA)
                    if (X.nextA == F) {
                        X.nextA = E;
                        break;
                    }
            }
            else if (F.segB == this || F.segB == nextSeg) {
                // doing B
                for (Intersection X = src; X != dst && X != null; X = X.nextB)
                    if (X.nextB == F) {
                        X.nextB = E;
                        break;
                    }
            }
            else assert false;
        }
    }

    /** We've discovered that these 2 pts, on opposite polygons, are the same.
     * Update * so that both rings refer to the same Intersection. */
    private Intersection unify(Intersection E, Intersection F)
    {
        if (E.segA == null || E.segB == null) {
            Intersection tmp = E;
            E = F;
            F = tmp;
        }

        // Merge into E:
        if (E.nextA == null)
            E.nextA = F.nextA;
        if (E.nextB == null)
            E.nextB = F.nextB;
        if (F.segA != null)
            F.segA.wasUnified(F,E);
        if (F.segB != null)
            F.segB.wasUnified(F,E);
        return E;
    }

    /** This can be overridden if you're extending Polygon. */
    protected Polygon newPolygon()
    {
        return new Polygon();
    }

    /** Remember that polygons always go clockwise.
     * If 'pt' is on the left of AB, i.e. outside, return -1.
     * If it lies on the right, i.e. inside, return 1.
     * If it's exactly on this segment, return 0. */
    public static byte whichSide(LatLong A, LatLong B, LatLong pt)
    {
        double Dx = (A.lng - B.lng);
        double Dy = (A.lat - B.lat);
        double Ax = (A.lng - pt.lng);
        double Ay = (A.lat - pt.lat);
        double c = (Dx*Ay - Ax*Dy);
        if (c == 0)
            return 0;
        else if (c < 0)
            return 1;
        else return -1;
    }

    /** @return 1=inside
     *          2=on the outgoing arc BC
     *          -1=outside
     *          -2=on the incoming arc AB */
    public wedgeEnum whichSideOfWedge(LatLong A, LatLong B, LatLong C, LatLong pt)
    {
        byte bc = whichSide(B,C, pt);
        byte ab = whichSide(A,B, pt);
        if (bc == ab) {
            // The common case:
            if (bc != 0)
                return bc == 1 ? wedgeEnum.inside : wedgeEnum.outside;

            // Collinear
            if (Math.abs(A.lat - C.lat) > Math.abs(A.lng - C.lng)) {
                if (A.lat > C.lat)
                    return pt.lat > B.lat ? wedgeEnum.alongIncoming : wedgeEnum.alongOutgoing;
                else return pt.lat < B.lat ? wedgeEnum.alongIncoming : wedgeEnum.alongOutgoing;
            }
            else {
                if (A.lng > C.lng)
                    return pt.lng > B.lng ? wedgeEnum.alongIncoming : wedgeEnum.alongOutgoing;
                else return pt.lng < B.lng ? wedgeEnum.alongIncoming : wedgeEnum.alongOutgoing;
            }
        }
        if (whichSide(A,B, C) > 0) {
            // Convex
            if (ab < 0 || bc < 0)
                return wedgeEnum.outside;
            else return bc > 0 ? wedgeEnum.alongIncoming : wedgeEnum.alongOutgoing;
        }
        else {
            // Concave
            if (ab > 0 || bc > 0)
                return wedgeEnum.inside;
            else return bc < 0 ? wedgeEnum.alongIncoming : wedgeEnum.alongOutgoing;
        }
    }

    @Override
    public boolean intersects(BoundingBoxOrPolygon other)
    {
        List<BoundingBoxOrPolygon> overlap = other.intersection(this);
        return overlap != null && ! overlap.isEmpty();
    }



    /*-------------------------------------------------------*/

    @Override
    public boolean isEmpty()
    {
        return points.size() <= 2;
    }

    @Override public BoundingBox getBoundingBox() { return box; }

    @Override
    public List<LatLong> getLatLngs() {
        return points;
    }

    /** Make this polygon larger on all sides by 'metres'. */
    @Override
    public void enlarge(int metres)
    {
        LatLong centre = box.center();
        for (LatLong latLng : points) {
            latLng.moveTowards(centre, -metres);
        }
        calcBoundingBox();
    }

    public void calcBoundingBox()
    {
        box = new BoundingBox(false);
        for (LatLong latLng : points)
            box.extendToFit(latLng);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
            return true;
        if (! (obj instanceof Polygon))
            return false;
        Polygon other = (Polygon)obj;
        int n = points.size();
        for (int i=0; i < n; i++) {
            if (pointsEqual(i, other.points))
                return true;
        }
        return false;
    }

    private boolean pointsEqual(int n, List<LatLong> otherPoints)
    {
        for (LatLong pt : points) {
            if (n >= otherPoints.size())
                n = 0;
            if (! pt.equals(otherPoints.get(n++)))
                return false;
        }
        return true;
    }

    public static void outputToKml(Polygon ... A)
    {
        List<Polygon> polygons = new ArrayList<>();
        for (Polygon polygon : A)
            polygons.add(polygon);
        outputToKml(polygons);
    }

    public static void outputToKml(Collection<Polygon> polygons)
    {
        FileWriter writer = null;
        try {
            writer = new FileWriter("C:\\SkedgoData\\kmls\\polys.kml");
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n" +
                    "  <Document>\n" +
                    "    <name>KML Samples</name>\n" +
                    "    <open>1</open>\n" +
                    "    <Style id=\"yellow\">\n" +
                    "      <LineStyle>\n" +
                    "          <width>3</width>\n" +
                    "          <color>7d00ffff</color>\n" +
                    "      </LineStyle>\n" +
                    "    </Style>\n" +
                    "    <Style id=\"red\">\n" +
                    "      <LineStyle>\n" +
                    "          <width>3</width>\n" +
                    "          <color>7d0000ff</color>\n" +
                    "      </LineStyle>\n" +
                    "    </Style>\n" +
                    "    <Style id=\"green\">\n" +
                    "      <LineStyle>\n" +
                    "          <width>3</width>\n" +
                    "          <color>7d00ff00</color>\n" +
                    "      </LineStyle>\n" +
                    "    </Style>\n" +
                    "    <Style id=\"blue\">\n" +
                    "      <LineStyle>\n" +
                    "          <width>3</width>\n" +
                    "          <color>7dff0000</color>\n" +
                    "      </LineStyle>\n" +
                    "    </Style>\n" +
                    "    <Style id=\"purple\">\n" +
                    "      <LineStyle>\n" +
                    "          <width>3</width>\n" +
                    "          <color>7dff00ff</color>\n" +
                    "      </LineStyle>\n" +
                    "    </Style>\n" +
                    "    <Folder>\n" +
                    "        <name>Tim's</name>\n");
            String[] style = { "yellow", "red", "green", "blue", "purple" };
            int n = 0;
            for (Polygon polygon : polygons) {
                if (polygon == null)
                    continue;
                if (n == style.length)
                    n = 0;
                polygon.outputOneToKml(writer, style[n++]);
            }
            writer.append("      </Folder>\n" +
                    "  </Document>\n" +
                    "</kml>\n");
            writer.close();
        } catch (IOException e) {
        }
    }

    private void outputOneToKml(FileWriter writer, String style) throws IOException
    {
        writer.append("        <Placemark>\n" +
                "            <styleUrl>#" + style + "</styleUrl>\n" +
                "            <LineString>\n" +
                "                <coordinates>");
        boolean needComma = false;

        // Repeat the end-point:
        LatLong last = points.get(points.size()-1);
        writer.append(last.lng + "," + last.lat);
        needComma = true;

        //
        for (LatLong point : points) {
            if (needComma)
                writer.append("\n          ");
            writer.append(point.lng + "," + point.lat);
            needComma = true;
        }
        writer.append("</coordinates>\n" +
                "            </LineString>\n" +
                "        </Placemark>\n");
    }



    /*--------------------------- Ramer-Douglas-Peucker sub-sampling of complex polygons ----------------------------*/

    public void simplify(double maxError)
    {
        LatLong[] P = points.toArray(new LatLong[0]);
        points = new ArrayList<>();
        int top=-1,right=-1,bottom=-1,left=-1;
        for (int i=0; i < P.length; i++) {
            LatLong pt = P[i];
            if (pt.lat == box.lat2)
                top = i;
            if (pt.lat == box.lat1)
                bottom = i;
            if (pt.lng == box.lng1)
                left = i;
            if (pt.lng == box.lng2)
                right = i;
        }
        douglasPeucker(top, right, P, maxError);
        douglasPeucker(right,bottom, P, maxError);
        douglasPeucker(bottom,left, P, maxError);
        douglasPeucker(left, top, P, maxError);
        cleanUp();
        calcBoundingBox();
    }

    /** Output to 'points' P[i1] plus any necessary intermediate points, but not 'P[i2]'. */
    private void douglasPeucker(int i1, int i2, LatLong[] P, double epsilon)
    {
        douglasPeuckerRecurse(i1, i2, P, epsilon);
    }

    /** Output to 'points' P[i1] plus any necessary intermediate points, but not 'P[i2]'. */
    private void douglasPeuckerRecurse(int i1, int i2, LatLong[] P, double epsilon)
    {
        if (i1 == i2)
            return;
        if (i1 < 0 || i2 < 0)
            return;

        // Find the point with the maximum distance
        double dmax = 0;
        int index = 0;
        LatLong A = P[i1];
        LatLong B = P[i2];
        for (int i = i1; i != i2; ) {
            if (++i >= P.length)
                i = 0;
            double d = P[i].distanceToLineSegment(A, B);
            if (d > dmax) {
                index = i;
                dmax = d;
            }
        }

        // If max distance is greater than epsilon, recursively simplify
        if (dmax > epsilon) {
            // Recursive call
            douglasPeuckerRecurse(i1,index, P,epsilon);
            if (isOnBoundingBox(P[index]))
                points.add(P[index]);
            douglasPeuckerRecurse(index, i2, P, epsilon);
        } else {
            points.add(P[i1]);
        }
    }

    private boolean isOnBoundingBox(LatLong pt)
    {
        if (pt.lat == box.lat1 || pt.lat == box.lat2)
            return true;
        if (pt.lng == box.lng1 || pt.lng == box.lng2)
            return true;
        return false;
    }


    /*------------------------- Find internal intersections --------------------*/

    public List<Polygon> separateSelfIntersectingPolygons()
    {
        List<Polygon> polys = new ArrayList<>();
        try {
            separateSelfIntersector(polys);
        } catch (PolygonException e) {
        }
        return polys;
    }

    private void separateSelfIntersector(List<Polygon> polys) throws PolygonException {
        Intersection2 X = findInternalIntersections();
        if (X == null) {
            if (points.size() > 2) {
                ensureIsClockwise();
                polys.add(this);
            }
        }
        else {
            Polygon left = makeSmallerPolygon(X.i1, X.i2, X.X);
            Polygon right = makeSmallerPolygon(X.i2, X.i1, X.X);
            left.separateSelfIntersector(polys);
            right.separateSelfIntersector(polys);
        }
    }

    private Polygon makeSmallerPolygon(int i1, int i2, Intersection X) throws PolygonException
    {
        Polygon polygon = newPolygon();
        polygon.points.add(X);
        for (int i=(i1+1==points.size())?0:i1+1; i != i2; ) {
            polygon.addPtWithEqualityCheck(points.get(i));
            if (++i >= points.size())
                i = 0;
        }
        polygon.calcBoundingBox();
        //if (polygon.points.size() > 2)
        //    assert polygon.check();
        return polygon;
    }

    private class Segment2 extends BoundingBox {
        int i;

        Segment2(int _i)
        {
            super(false);
            i = _i;
            extendToFit(A());
            extendToFit(B());
        }

        LatLong A() { return points.get(i); }
        LatLong B() { return points.get(i+1>=points.size()?0:i+1); }
    }

    private Intersection2 findInternalIntersections()
    {
        List<Segment2> segs = new ArrayList<>();
        for (int i=0; i < points.size(); i++) {
            segs.add(new Segment2(i));
        }
        Segment2[] entering = segs.toArray(new Segment2[0]);
        Segment2[] leaving = segs.toArray(new Segment2[0]);
        Arrays.sort(entering, new Comparator<Segment2>() {
            @Override
            public int compare(Segment2 seg1, Segment2 seg2) {
                return Double.compare(seg1.lng1, seg2.lng1);
            }
        });
        Arrays.sort(leaving, new Comparator<Segment2>() {
            @Override
            public int compare(Segment2 seg1, Segment2 seg2) {
                return Double.compare(seg1.lng2, seg2.lng2);
            }
        });
        int e=0, l = 0;
        List<Segment2> loom = new ArrayList<>();
        do {
            if (e < entering.length && entering[e].lng1 <= leaving[l].lng2) {
                Segment2 newSeg = entering[e++];
                Intersection2 X = examineForSelfIntersection(loom, newSeg);
                if (X != null)
                    return X;
                loom.add(newSeg);
            }
            else loom.remove(leaving[l++]);
        } while (l < leaving.length);
        return null;
    }

    private static class Intersection2 {
        int i1,i2;
        Intersection X;
    }

    private Intersection2 examineForSelfIntersection(List<Segment2> loom, Segment2 newSeg)
    {
        for (Segment2 old : loom) {
            if (old.intersects(newSeg)) {
                LatLong A = old.A();
                LatLong B = old.B();
                LatLong C = newSeg.A();
                LatLong D = newSeg.B();
                if (A == C || A == D || B == C || B == D)
                    continue;
                Intersection X = segmentIntersectPointWorker(A,B,C,D);
                if (X != null) {
                    Intersection2 X2 = new Intersection2();
                    X2.i1 = old.i;
                    X2.i2 = newSeg.i;
                    X2.X = X;
                    return X2;
                }
            }
        }
        return null;
    }

    public static class PolygonException extends Exception {
        Polygon bad;

        public PolygonException()
        {

        }

        public PolygonException(Polygon _bad)
        {
            bad = _bad;
        }
    }
}
