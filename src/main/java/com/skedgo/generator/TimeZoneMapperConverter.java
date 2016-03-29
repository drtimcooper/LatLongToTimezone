package com.skedgo.generator;

import com.skedgo.generator.parsing.JSONArray;
import com.skedgo.generator.parsing.JSONException;
import com.skedgo.generator.parsing.JSONObject;
import com.skedgo.generator.parsing.JSONTokener;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


/**
 * I took shapefiles from here:   http://efele.net/maps/tz/world/
 *
 * I processed them into json according to a C library I found referenced in here:
 *          http://en.wikipedia.org/wiki/Shapefile
 *
 * Warning: we should modify the logic to treat counterclockwise polygons as negative polygons,
 * e.g. to remove ACT from NSW.  We could do this by simply eliminating all counterclockwise
 * polygons and then sorting polygons to have the smallest bounding boxes occur first, so that
 * we'd process ACT before NSW.  So far I just can't be bothered - I bet this is rare and I bet
 * it's even rarer that you get different offsets.
 *
 * 6/8/2015 Micah Martin <micahmartin@gmail.com> said:

 I thought I’d drop you a line to let you know I tried out your code.  So far it looks like it’ll prove quite useful for me…. it’s fast!

 My project is in Clojure so I built a wrapper and pushed it to the clojars maven repo.
 https://github.com/slagyr/latlngtz
 https://clojars.org/com.slagyr/latlngtz

 Thanks!

 Micah

 */
public class TimeZoneMapperConverter {

    private Map<String,Integer> tzstringToIntMap = new HashMap<>();
    private int tzNum=1;
    private List<TimezonePolygon> inputPolygons;
    private List<String> int2tzstring = new ArrayList<>();
    private double shownProgress;

    private final LatLong paris = new LatLong(48.856696,2.352077);
    private final LatLong chattanooga = new LatLong(35.03217, -85.19392);
    private final LatLong goldcoast = new LatLong(-28.019981, 153.428073);
    private final LatLong palmsprings = new LatLong(33.84531, -116.50513);
    private final LatLong chicago = new LatLong(41.8788764, -87.6359149);
    private final LatLong oulu = new LatLong(65.012197, 25.471152);
    private final LatLong frenchPyrenees = new LatLong(42.75676, -0.092723);    // very close to Spanish border
    private final LatLong northernItaly = new LatLong(46.51951, 12.008678);

    static int serialL;
    static long seed = System.currentTimeMillis();
    static Random random = new Random(seed);


    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: " + TimeZoneMapperConverter.class.getSimpleName()
                + " <json input file> <output file base (no extension)>");
            return;
        }

        try {
            new TimeZoneMapperConverter().go(args[0], args[1]);
        } catch (Exception e) {
            System.out.println("Failed with exception: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    public void go(String inputFilename, String outputFilename) throws IOException, JSONException
    {
        readPolygons(inputFilename);
        test1();
        makeKdTree();
        TzNode succinctRoot = convertToSuccinct(kdRoot);
        outputJavaSource(succinctRoot, outputFilename + ".java");
        outputSwiftSource(succinctRoot, outputFilename + ".swift");
        test3(succinctRoot);
        System.out.println("Finished.  Have a look at: " + outputFilename + ".java / .swift");
    }

    private void test1()
    {
        for (TimezonePolygon poly : inputPolygons) {
            if (poly.contains(chicago))
                System.out.println("Chicago is in " + poly.tzstring);
            if (poly.contains(palmsprings))
                System.out.println("Palm Springs is in " + poly.tzstring);
            if (poly.contains(paris))
                System.out.println("Paris is in " + poly.tzstring);
            if (poly.contains(goldcoast))
                System.out.println("Gold Coast is in " + poly.tzstring);
            if (poly.contains(oulu))
                System.out.println("Oulu is in " + poly.tzstring);
        }
    }

    private void test3(TzNode succinctRoot)
    {
        System.out.println("Succinct has Chicago in: " + succinctRoot.getTimezone(chicago));
        System.out.println("Succinct has Palm Springs in: " + succinctRoot.getTimezone(palmsprings));
        System.out.println("Succinct has Chattanooga in: " + succinctRoot.getTimezone(chattanooga));
        System.out.println("Succinct has Paris in: " + succinctRoot.getTimezone(paris));
        System.out.println("Succinct has GoldCoast in: " + succinctRoot.getTimezone(goldcoast));
        System.out.println("Succinct has Oulu in: " + succinctRoot.getTimezone(oulu));
        System.out.println("Succinct has French Pyrenees in: " + succinctRoot.getTimezone(frenchPyrenees));
        System.out.println("Succinct has northern Italy in: " + succinctRoot.getTimezone(northernItaly));
    }

    private void debug() throws Polygon.PolygonException {
        if (! kdRoot.findTz(chicago).equals("America/Chicago")) {
            kdRoot.findTz(chicago);
            kdRoot.findTz(chicago);
            throw new Polygon.PolygonException();
        }
        if (! kdRoot.findTz(oulu).equals("Europe/Helsinki")) {
            kdRoot.findTz(oulu);
            kdRoot.findTz(oulu);
            throw new Polygon.PolygonException();
        }
        if (! kdRoot.findTz(palmsprings).equals("America/Los_Angeles")) {
            kdRoot.findTz(palmsprings);
            kdRoot.findTz(palmsprings);
            throw new Polygon.PolygonException();
        }
    }


    /*-------------------------- Reading the input polygons: ----------------------*/

    class TimezonePolygon extends Polygon {
        String tzstring;
        int tz;

        public TimezonePolygon(String _tzstring)
        {
            tzstring = _tzstring;
            Integer i = tzstringToIntMap.get(tzstring);
            if (i == null) {
                i = tzNum++;
                int2tzstring.add(tzstring);
                tzstringToIntMap.put(tzstring, i);
            }
            tz = i;
        }

        public TimezonePolygon()
        {
        }

        public int size() {
            return points.size();
        }

        /** This can be overridden if you're extending Polygon. */
        @Override
        protected Polygon newPolygon()
        {
            TimezonePolygon tzPoly = new TimezonePolygon();
            tzPoly.tz = tz;
            tzPoly.tzstring = tzstring;
            return tzPoly;
        }

        public String toString()
        {
            return tzstring + " " + super.toString();
        }

        public void toJavaSource(FileWriter writer, int i) throws IOException
        {
            writer.append("new TzPolygon(");
            int chars = 20;
            for (LatLong pt : points) {
                if (pt != points.get(0)) {
                    if (chars > 110) {
                        writer.append("f,\r\n\t\t");
                        chars = 16;
                    }
                    else writer.append("f, ");
                }
                writer.append(Util.formatNumber(pt.lat, 6));
                writer.append("f,");
                writer.append(Util.formatNumber(pt.lng, 6));
                chars += 22;
            }
            writer.append("f)");
        }

        public void toSwiftSource(FileWriter writer, int i) throws IOException
        {
            writer.append("[");
            int chars = 20;
            for (LatLong pt : points) {
                if (pt != points.get(0)) {
                    if (chars > 110) {
                        writer.append(",\n\t\t");
                        chars = 16;
                    }
                    else writer.append(", ");
                }
                writer.append(Util.formatNumber(pt.lat, 6));
                writer.append(",");
                writer.append(Util.formatNumber(pt.lng, 6));
                chars += 22;
            }
            writer.append("]");
        }
    }

    static void outputIndent(FileWriter writer, int indent) throws IOException
    {
        writer.append("\t");
        for (int i=0; i < indent; i++)
            writer.append(' ');
    }

    private void readPolygons(String inputFilename) throws FileNotFoundException, JSONException
    {
        int2tzstring.add("unknown");        // This will be timezone 0.
        System.out.println("Reading polygons");
        JSONArray json = new JSONArray(new JSONTokener(new FileReader(inputFilename)));
        inputPolygons = new ArrayList<>();
        for (Object obj : json) {
            JSONObject jPoly = (JSONObject)obj;
            String tz = jPoly.getString("tz");
            if (tz.equals("uninhabited"))
                continue;
            JSONArray points = jPoly.optJSONArray("polygon");
            TimezonePolygon polygon = new TimezonePolygon(tz.intern());
            for (Object obj2 : points) {
                JSONArray jPt = (JSONArray)obj2;
                double lat = jPt.getDouble(1);
                double lng = jPt.getDouble(0);
                polygon.addPoint(lat,lng);
            }
            polygon.cleanUp();
            if (polygon.isClockwise()) {
                polygon.simplify(1500); //1.5km
                if (polygon.size() > 2 && polygon.isClockwise()) {
                    List<Polygon> polys = polygon.separateSelfIntersectingPolygons();
                    for (Polygon poly : polys) {
                        if (new LatLong(poly.box.lat1, poly.box.lng1).distanceInMetres(new LatLong(poly.box.lat2, poly.box.lng2)) < 2000)
                            ;       // It's too small, probably an artifact of smoothing.
                        else inputPolygons.add((TimezonePolygon)poly);
                    }
                }
            }
        }
        System.out.println("Finished reading.");
    }



    /*-------------------------- Indexing with KD-trees: ----------------------*/

    class KdTree extends BoundingBox {
        List<TimezonePolygon> polys = new ArrayList<>();
        double pivot;
        boolean pivotOnLat;
        KdTree left,right;

        /** 0=multiple timezones. */
        int tz;

        public KdTree()
        {
            super(true);
        }

        public KdTree(double lat1, double lat2, double lng1, double lng2)
        {
            super(lat1,lat2,lng1,lng2);
        }

        public void splitAsNecessary(int depth, double progress1, double progress2)
        {
            do {
                try {
                    splitAsNecessaryWorker(depth, progress1, progress2);
                    break;
                } catch (Exception e) {
                }
            } while (true);
        }

        public void splitAsNecessaryWorker(int depth, double progress1, double progress2) throws Polygon.PolygonException {

            // Is the job already done?
            tz = pureTimezone();
            if (tz != 0)
                return;
            if (polys.size() <= 1)
                return;

            // Special logic when we get down to 2 polys:
            if (polys.size() == 2) {
                split2Polys();
                return;
            }

            // The normal case:
            double scoreLat = whatIfSplit(true);
            double pivotLat = pivot;
            double scoreLng = whatIfSplit(false);
            double pivotLng = pivot;
            pivotOnLat = scoreLat < scoreLng;
            int maxScore = polys.size();
            maxScore *= maxScore;
            if (pivotOnLat) {
                if (scoreLat >= maxScore) {
                    splitJustToReduceComplexityInPolygons();
                    return;// We can't divide these tzPolys up any further by horizontal or vertical cut lines.
                }
                pivot = pivotLat;
                left = new KdTree(lat1, pivot, lng1, lng2);
                right = new KdTree(pivot, lat2, lng1, lng2);
                for (TimezonePolygon poly : polys) {
                    if (poly.box.lat1 < pivot)
                        left.add(poly.intersection(left));
                    if (poly.box.lat2 > pivot)
                        right.add(poly.intersection(right));
                }
            }
            else {
                if (scoreLng >= maxScore) {
                    splitJustToReduceComplexityInPolygons();
                    return;// We can't divide these tzPolys up any further by horizontal or vertical cut lines.
                }
                pivot = pivotLng;
                left = new KdTree(lat1, lat2, lng1, pivot);
                right = new KdTree(lat1, lat2, pivot, lng2);
                for (TimezonePolygon poly : polys) {
                    if (poly.box.lng1 < pivot)
                        left.add(poly.intersection(left));
                    if (poly.box.lng2 > pivot)
                        right.add(poly.intersection(right));
                }
            }
            depth++;
            double midProgress = (progress1 + progress2) / 2;
            left.splitAsNecessary(depth, progress1, midProgress);
            right.splitAsNecessary(depth, midProgress, progress2);
            polys = null;

            // Display a progress indicator:
            int progress = (int)progress2;
            if (progress != shownProgress) {
                System.out.println("[" + progress + "%]");
                shownProgress = progress;
            }
        }

        private void split2Polys() throws Polygon.PolygonException {
            if (polys.size() <= 1)
                return;
            TimezonePolygon poly1 = polys.get(0);
            TimezonePolygon poly2 = polys.get(1);
            BoundingBox box1 = poly1.box;
            BoundingBox box2 = poly2.box;
            if (box1.lat1 > box2.lat2) {
                pivotOnLat = true;
                pivot = (box1.lat1 + box2.lat2) / 2;
                left = new KdTree(lat1, pivot, lng1, lng2);
                right = new KdTree(pivot, lat2, lng1, lng2);
            }
            else if (box1.lat2 < box2.lat1) {
                pivotOnLat = true;
                pivot = (box1.lat2 + box2.lat1) / 2;
                left = new KdTree(lat1, pivot, lng1, lng2);
                right = new KdTree(pivot, lat2, lng1, lng2);
            }
            else if (box1.lng1 > box2.lng2) {
                pivotOnLat = false;
                pivot = (box1.lng1 + box2.lng2) / 2;
                left = new KdTree(lat1, lat2, lng1, pivot);
                right = new KdTree(lat1, lat2, pivot, lng2);
            }
            else if (box1.lng2 < box2.lng1) {
                pivotOnLat = false;
                pivot = (box1.lng2 + box2.lng1) / 2;
                left = new KdTree(lat1, lat2, lng1, pivot);
                right = new KdTree(lat1, lat2, pivot, lng2);
            }
            else {
                // Their bounding boxes overlap and there's no easy way to split them.
                // But we'll split anyway if there's an especially large polygon.
                splitJustToReduceComplexityInPolygons();
                return;
            }
            left.polys.add(poly1);
            right.polys.add(poly2);
            left.tz = left.pureTimezone();
            right.tz = right.pureTimezone();
            polys = null;
        }

        private void splitJustToReduceComplexityInPolygons() throws Polygon.PolygonException {
            if (polys == null)
                return;
            if (complexity() > 50) {
                BoundingBox box = new BoundingBox(false);
                for (TimezonePolygon poly : polys)
                    box.extendToFit(poly.box);
                pivotOnLat = (box.lat2 - box.lat1 > box.lng2 - box.lng1);
                if (pivotOnLat) {
                    pivot = (box.lat1 + box.lat2) / 2;
                    left = new KdTree(lat1, pivot, lng1, lng2);
                    right = new KdTree(pivot, lat2, lng1, lng2);
                }
                else {
                    pivot = (box.lng1 + box.lng2) / 2;
                    left = new KdTree(lat1, lat2, lng1, pivot);
                    right = new KdTree(lat1, lat2, pivot, lng2);
                }
                for (TimezonePolygon poly : polys) {
                    if ((pivotOnLat?poly.box.lat1:poly.box.lng1) < pivot)
                        left.add(poly.intersection(left));
                    if ((pivotOnLat?poly.box.lat2:poly.box.lng2) > pivot)
                        right.add(poly.intersection(right));
                }
                left.splitJustToReduceComplexityInPolygons();
                right.splitJustToReduceComplexityInPolygons();
                polys = null;
                left.tz = left.pureTimezone();
                right.tz = right.pureTimezone();
            }
        }

        private void add(List<BoundingBoxOrPolygon> polygons) throws Polygon.PolygonException {
            if (polygons == null)
                return;
            for (BoundingBoxOrPolygon poly : polygons) {
                add((TimezonePolygon)poly);
            }
        }

        private void add(TimezonePolygon tzPolygon) throws Polygon.PolygonException {
            if (! tzPolygon.isClockwise())
                throw new Polygon.PolygonException();
            polys.add(tzPolygon);
        }

        /** Return 0 if there are multiple timezones represented. Return -1 if no timezones. */
        private int pureTimezone()
        {
            int tz1=-1;
            if (polys == null)
                return 0;
            for (TimezonePolygon tzPoly : polys) {
                if (tzPoly.tz == 0 || tzPoly.tz == tz1)
                    continue;
                if (tz1 == -1)
                    tz1 = tzPoly.tz;
                else return 0;
            }
            return tz1;
        }

        private double whatIfSplit(boolean splitOnLat)
        {
            double[] interesting = new double[polys.size()*2];
            int n = 0;
            for (TimezonePolygon poly : polys) {
                interesting[n++] = splitOnLat ? poly.box.lat1 : poly.box.lng1;
                interesting[n++] = splitOnLat ? poly.box.lat2 : poly.box.lng2;
            }
            Arrays.sort(interesting);
            double bestScore = Double.MAX_VALUE;
            double bestPivot = 0;
            int n1 = 1;
            int n2 = interesting.length-1;
            if (n2 > 1000) {
                int c = n2 / 2;
                n1 = c - 500;
                n2 = c + 500;
            }
            int step = 1;
            if (n2 - n1 > 100)
                step = 5;
            for (n=n1; n < n2; n += step) {
                double score = whatIfSplit(interesting[n], splitOnLat);
                // For randomising the structure:   score *= 1.0 + 0.1 * random.nextDouble();
                if (score < bestScore) {
                    bestScore = score;
                    bestPivot = interesting[n];
                }
            }
            pivot = bestPivot;
            return bestScore;
        }

        private double whatIfSplit(double pivot, boolean splitOnLat)
        {
            int nLeft=0, nRight=0;
            int leftTz=-1, rightTz=-1;
            for (TimezonePolygon poly : polys) {
                double rightMax,leftMin;
                if (splitOnLat) {
                    rightMax = poly.box.lat2;
                    leftMin = poly.box.lat1;
                }
                else {
                    rightMax = poly.box.lng2;
                    leftMin = poly.box.lng1;
                }
                assert poly.tz != 0;
                if (rightMax > pivot) {
                    nRight++;
                    if (rightTz < 0)
                        rightTz = poly.tz;
                    else if (rightTz != poly.tz)
                        rightTz = 0;
                }
                if (leftMin < pivot) {
                    nLeft++;
                    if (leftTz < 0)
                        leftTz = poly.tz;
                    else if (leftTz != poly.tz)
                        leftTz = 0;
                }
            }
            if (leftTz > 0)
                nLeft = 1;      // All polys on that side are with the same timezone.
            if (rightTz > 0)
                nRight = -1;      // All polys on that side are with the same timezone.
            return (double)nLeft*nLeft + (double)nRight*nRight;
        }

        int complexity()
        {
            if (polys == null)
                return left.complexity() + right.complexity();
            int defaultTz = mostCommonTz(polys);
            int n = 1;
            for (TimezonePolygon poly : polys) {
                if (poly.tz != defaultTz)
                    n += poly.size();
            }
            return n;
        }

        public String findTz(LatLong pt)
        {
            if (left == null && polys != null) {
                if (tz != 0)
                    return int2tzstring.get(tz);
                for (TimezonePolygon poly : polys)
                    if (poly.contains(pt)) {
                        return poly.tzstring;
                    }
                return "unknown";
            }
            if ((pivotOnLat?pt.lat:pt.lng) < pivot)
                return left.findTz(pt);
            else return right.findTz(pt);
        }
    }

    private KdTree kdRoot;

    private void makeKdTree()
    {
        System.out.println("Putting into KD-tree");
        kdRoot = new KdTree(-90,90,-180,180);
        kdRoot.polys.addAll(inputPolygons);
        kdRoot.splitAsNecessary(0, 0.0, 100.0);
        System.out.println("Finished putting into KD-tree");
    }

    private void check() throws Polygon.PolygonException {
        assert kdRoot.findTz(palmsprings).equals("America/Los_Angeles");
        assert kdRoot.findTz(goldcoast).equals("Australia/Brisbane");
        assert kdRoot.findTz(paris).equals("Europe/Paris");
        assert kdRoot.findTz(chattanooga).equals("America/New_York");
    }



    /*-------------------------- Expressing succinctly: ----------------------*/

    abstract class TzNode {
        int amountOfText;
        abstract String getTimezone(LatLong latLong);
        abstract void toJavaSource(FileWriter o, int indent) throws IOException;
        abstract void toSwiftSource(FileWriter o, int indent) throws IOException;
    }

    class PureTzNode extends TzNode {
        int tz;

        PureTzNode(int _tz)
        {
            tz = _tz;
            if (tz < 0)
                tz = 0;
            amountOfText = 1;
        }

        String getTimezone(LatLong latLong) { return int2tzstring.get(tz); }

        void toJavaSource(FileWriter o, int indent) throws IOException {
            outputIndent(o, indent);
            o.append("return " + tz + ";\r\n");
        }

        void toSwiftSource(FileWriter o, int indent) throws IOException {
            outputIndent(o, indent);
            o.append("return " + tz + "\r\n");
        }
    }

    class HorizontalVerticalTzNode extends TzNode {
        double pivot;
        boolean pivotOnLat;
        TzNode left,right;

        HorizontalVerticalTzNode(TzNode _left, TzNode _right)
        {
            left = _left;
            right = _right;
            if (left.amountOfText > 100)
                left = new SeparateMethodTzNode(left);
            if (right.amountOfText > 100)
                right = new SeparateMethodTzNode(right);
            amountOfText = left.amountOfText + right.amountOfText;
        }

        String getTimezone(LatLong latLng)
        {
            return (pivotOnLat ? latLng.lat : latLng.lng)
                    < pivot ? left.getTimezone(latLng) : right.getTimezone(latLng);
        }

        void toJavaSource(FileWriter o, int indent) throws IOException
        {
            outputIndent(o, indent);
            o.append(pivotOnLat ? "if (lat < " : "if (lng < ");
            o.append(Util.formatNumber(pivot, 6));
            o.append("f)\r\n");
            left.toJavaSource(o, indent+1);
            outputIndent(o, indent);
            o.append("else\r\n");
            right.toJavaSource(o, indent+1);
        }

        void toSwiftSource(FileWriter o, int indent) throws IOException
        {
            outputIndent(o, indent);
            o.append(pivotOnLat ? "if lat < " : "if lng < ");
            o.append(Util.formatNumber(pivot, 6));
            o.append(" {\r\n");
            left.toSwiftSource(o, indent + 1);
            outputIndent(o, indent);
            o.append("} else {\r\n");
            right.toSwiftSource(o, indent + 1);
            outputIndent(o, indent);
            o.append("}\r\n");
        }
    }

    class PolygonTzNode extends TzNode {
        List<TimezonePolygon> polys;

        public PolygonTzNode(List<TimezonePolygon> _polys)
        {
            polys = _polys;
            amountOfText = polys.size();
        }

        String getTimezone(LatLong latLng)
        {
            for (TimezonePolygon tzPoly : polys)
                if (tzPoly.contains(latLng))
                    return tzPoly.tzstring;
            return null;
        }

        void toJavaSource(FileWriter o, int indent) throws IOException
        {
            // Output polygon things for the others:
            int defaultTz = mostCommonTz(polys);
            outputIndent(o, indent);
            boolean needsElse = false;
            o.append("{\r\n");
            for (TimezonePolygon tzPoly : polys) {
                if (tzPoly.tz == defaultTz)
                    continue;
                outputIndent(o, indent);
                int n = polygonsForOutput.size();
                o.append("if (poly[" + n + "].contains(lat,lng)) return " + tzPoly.tz + ";\r\n");
                polygonsForOutput.add(tzPoly);
                needsElse = true;
            }
            assert needsElse;   // otherwise it'd be a pure thing
            outputIndent(o, indent);
            o.append("else return " + defaultTz + ";\r\n");
            outputIndent(o, indent);
            o.append("}\r\n");
        }

        void toSwiftSource(FileWriter o, int indent) throws IOException
        {
            // Output polygon things for the others:
            int defaultTz = mostCommonTz(polys);
            boolean needsElse = false;
            for (TimezonePolygon tzPoly : polys) {
                if (tzPoly.tz == defaultTz)
                    continue;
                outputIndent(o, indent);
                int n = polygonsForOutput.size();
                o.append("if poly[" + n + "].contains(testy: lat, testx: lng) { return " + tzPoly.tz + " }\r\n");
                polygonsForOutput.add(tzPoly);
                needsElse = true;
            }
            assert needsElse;   // otherwise it'd be a pure thing
            outputIndent(o, indent);
            o.append("else { return " + defaultTz + " } \r\n");
        }
    }

    class SeparateMethodTzNode extends TzNode {
        TzNode body;
        int methodNum;

        SeparateMethodTzNode(TzNode _node)
        {
            amountOfText = 0;
            body = _node;
            methodNum = methodsForOutput.size();
            methodsForOutput.add(this);
        }

        @Override
        String getTimezone(LatLong latLong)
        {
            return body.getTimezone(latLong);
        }

        @Override
        void toJavaSource(FileWriter o, int indent) throws IOException
        {
            outputIndent(o, indent);
            o.append("return call" + methodNum + "(lat,lng);\r\n");
        }

        @Override
        void toSwiftSource(FileWriter o, int indent) throws IOException
        {
            outputIndent(o, indent);
            o.append("return call" + methodNum + "(lat: lat, lng: lng)\r\n");
        }
    }

    private int mostCommonTz(List<TimezonePolygon> polys)
    {
        // What's the most common tz?
        if (polys.isEmpty())
            return 0;
        Collections.sort(polys, new Comparator<TimezonePolygon>() {
            @Override
            public int compare(TimezonePolygon poly1, TimezonePolygon poly2)
            {
                return poly1.tz - poly2.tz;
            }
        });
        int mostCommonTz = polys.get(0).tz;
        int mostCommonRunLength = 0;
        int runLength = 0;
        int prevTz = -2;
        for (TimezonePolygon tzPoly : polys) {
            if (tzPoly.tz != prevTz) {
                runLength = 2;
                prevTz = tzPoly.tz;
            }
            runLength += tzPoly.size();
            if (runLength > mostCommonRunLength) {
                mostCommonRunLength = runLength;
                mostCommonTz = tzPoly.tz;
            }
        }
        return mostCommonTz;
    }

    private List<TimezonePolygon> polygonsForOutput = new ArrayList<>();
    private List<SeparateMethodTzNode> methodsForOutput = new ArrayList<>();

    private void resetOutputCounts() {
        // Generating output modifies this collection, so it needs to be cleared
        // before generating output.
        polygonsForOutput.clear();
    }

    private TzNode convertToSuccinct(KdTree kd)
    {
        if (kd.tz != 0) {
            return new PureTzNode(kd.tz);
        }
        else if (kd.left != null) {
            TzNode left = convertToSuccinct(kd.left);
            TzNode right = convertToSuccinct(kd.right);
            HorizontalVerticalTzNode node = new HorizontalVerticalTzNode(left, right);
            node.pivotOnLat = kd.pivotOnLat;
            node.pivot = kd.pivot;
            return node;
        }
        else {
            assert kd.polys.size() > 1;//Pure KdTree nodes have the tz.
            return new PolygonTzNode(kd.polys);
        }
    }




    /*---------------------------- Writing to Java: -----------------------*/

    private void outputJavaSource(TzNode succinctRoot, String filename) throws IOException
    {
        resetOutputCounts();

        FileWriter writer = new FileWriter(filename);
        writer.append("/** The provided code is written by Tim Cooper:   tim@edval.com.au\r\n");
        writer.append("This code is available under the MIT licence:  https://opensource.org/licenses/MIT  */\n\n");
        writer.append("\npublic class TimezoneMapper {\r\n\r\n");

        // Entry-point method:
        writer.append("    public static String latLngToTimezoneString(double lat, double lng)\n" +
                "    {\n" +
                "        String tzId = timezoneStrings[getTzInt((float) lat, (float) lng)];\n" +
                "        return tzId;\n" +
                "    }\n" +
                "\n");

        // The timezone strings:
        writer.append("\tstatic String[] timezoneStrings = {\r\n");
        String finalTzs = int2tzstring.get(int2tzstring.size()-1);
        for (String s : int2tzstring) {
            writer.append("\t\"" + s + "\"");
            if (s != finalTzs)
                writer.append(",");
            writer.append("\r\n");
        }
        writer.append("\t};\r\n\r\n");

        // The main stuff:
        writer.append("\tprivate static int getTzInt(float lat, float lng)\n" +
                "\t{\r\n");
        succinctRoot.toJavaSource(writer, 1);
        writer.append("\t}\r\n\r\n");

        // The methods:
        for (SeparateMethodTzNode node : methodsForOutput) {
            writer.append("\tprivate static int call" + node.methodNum + "(float lat, float lng)\r\n\t{\r\n");
            node.body.toJavaSource(writer,1);
            writer.append("\t}\r\n\r\n");
        }

        // The Polygon class:
        writer.append("    private static class TzPolygon {\n" +
                "\n" +
                "        float[] pts;\n" +
                "\n" +
                "        TzPolygon(float ... D)\n" +
                "        {\n" +
                "            pts = D;\n" +
                "        }\n\n" +
                "        public boolean contains(float testy, float testx)\n" +
                "        {\n" +
                "            boolean inside = false;\n" +
                "            int n = pts.length;\n" +
                "            float yj = pts[n-2];\n" +
                "            float xj = pts[n-1];\n" +
                "            for (int i = 0; i < n; ) {\n" +
                "                float yi = pts[i++];\n" +
                "                float xi = pts[i++];\n" +
                "                if ( ((yi>testy) != (yj>testy)) && (testx < (xj-xi) * (testy-yi) / (yj-yi) + xi - 0.0001f))\n" +
                "                    inside = !inside;\n" +
                "                xj = xi;\n" +
                "                yj = yi;\n" +
                "            }\n" +
                "            return inside;\n" +
                "        }\n" +
                "    }\n" +
                "\n\n");

        // The polygons:
        writer.append("\tprivate static TzPolygon[] poly = initPolyArray();\r\n\r\n");
        int slab = 1;
        int idx = 0;
        do {
            writer.append("\r\n\tprivate static class Initializer" + slab + " {\r\n");
            writer.append("\t\tprivate static void init() {\r\n");
            int numInSlab = 0;
            do {
                TimezonePolygon tzPoly = polygonsForOutput.get(idx);
                writer.append("\t\t\tpoly[" + idx + "] = ");
                idx++;
                tzPoly.toJavaSource(writer, 3);
                writer.append(";\r\n");
            } while (idx < polygonsForOutput.size() && ++numInSlab < 100);
            writer.append("\t\t}\r\n");
            writer.append("\t}\r\n");
            slab++;
        } while (idx < polygonsForOutput.size());
        writer.append("\r\n\tstatic TzPolygon[] initPolyArray()\n" +
                "\t{\n" +
                "\t\tpoly = new TzPolygon[" + polygonsForOutput.size() + "];\n" +
                "\r\n");
        for (int i=1; i < slab; i++) {
            writer.append("\t\tInitializer" + i + ".init();\r\n");
        }
        writer.append("\t\treturn poly;\n" +
                "\t}\r\n\r\n");

        //
        writer.append("}\r\n\r\n");
        writer.close();
    }

       /*---------------------------- Writing to Swift: -----------------------*/

    private void outputSwiftSource(TzNode succinctRoot, String filename) throws IOException
    {
        resetOutputCounts();

        FileWriter writer = new FileWriter(filename);
        writer.append("/** The provided code is written by Tim Cooper:   tim@edval.com.au\n");
        writer.append(" * and Andrew Kirmse: akirmse@gmail.com\n");
        writer.append("This code is available under the MIT licence:  https://opensource.org/licenses/MIT  */\n");
        writer.append("import CoreLocation\n");
        writer.append("public class TimezoneMapper {\n\n");

        // Entry-point methods:
        writer.append("    public static func latLngToTimezoneString(location: CLLocationCoordinate2D) -> String\n" +
                "    {\n" +
                "        if poly.isEmpty {\n" +
                "            TimezoneMapper.initPolyArray()\n" +
                "        }\n" +
                "        let tzId = timezoneStrings[getTzInt(lat: Float(location.latitude), lng: Float(location.longitude))]\n" +
                "        return tzId\n" +
                "    }\n" +
                "    public static func latLngToTimezone(location: CLLocationCoordinate2D) -> NSTimeZone?\n" +
                "    {\n" +
                "        let tzId = latLngToTimezoneString(location)\n" +
                "        return NSTimeZone(name: tzId)\n" +
                "    }\n" +
                "\n");

        // The timezone strings:
        writer.append("\tprivate static let timezoneStrings = [\n");
        String finalTzs = int2tzstring.get(int2tzstring.size()-1);
        for (String s : int2tzstring) {
            writer.append("\t\"" + s + "\"");
            if (s != finalTzs)
                writer.append(",");
            writer.append("\n");
        }
        writer.append("\t]\n\n");

        // The main stuff:
        writer.append("\tprivate static func getTzInt(lat lat: Float, lng: Float) -> Int\n" +
                "\t{\n");
        succinctRoot.toSwiftSource(writer, 1);
        writer.append("\t}\n\n");

        // The methods:
        for (SeparateMethodTzNode node : methodsForOutput) {
            writer.append("\tprivate static func call" + node.methodNum + "(lat lat: Float, lng: Float) -> Int\n\t{\r\n");
            node.body.toSwiftSource(writer,1);
            writer.append("\t}\n\n");
        }

        // The Polygon class:
        writer.append("    private class TzPolygon {\n" +
                "\n" +
                "        let pts: [Float]\n" +
                "\n" +
                "        init(D: [Float])\n" +
                "        {\n" +
                "            pts = D\n" +
                "        }\n\n" +
                "        func contains(testy testy: Float, testx: Float) -> Bool\n" +
                "        {\n" +
                "            var inside = false\n" +
                "            let n = pts.count\n" +
                "            var yj = pts[n-2]\n" +
                "            var xj = pts[n-1]\n" +
                "            for var i = 0; i < n;  {\n" +
                "                let yi = pts[i++]\n" +
                "                let xi = pts[i++]\n" +
                "                if ((yi>testy) != (yj>testy)) {\n" +
                "                    if (testx < (xj-xi) * (testy-yi) / (yj-yi) + xi - Float(0.0001)) {\n" +
                "                        inside = !inside\n" +
                "                    }\n" +
                "                }\n" +
                "                xj = xi\n" +
                "                yj = yi\n" +
                "            }\n" +
                "            return inside\n" +
                "        }\n" +
                "    }\n" +
                "\n\n");

        // The polygons:
        writer.append("\tprivate static var poly = [TzPolygon]()\n\n");
        int slab = 1;
        int idx = 0;
        do {
            writer.append("\n\tprivate static func init" + slab + "() {\n");
            int numInSlab = 0;
            do {
                writer.append("\t\tlet poly" + idx + ": [Float] = ");
                TimezonePolygon tzPoly = polygonsForOutput.get(idx);
                tzPoly.toSwiftSource(writer, 1);
                writer.append("\n");
                writer.append("\t\tpoly.append(TzPolygon(D: poly" + idx + "))\n");
                idx++;
            } while (idx < polygonsForOutput.size() && ++numInSlab < 100);
            writer.append("\t}\n");
            slab++;
        } while (idx < polygonsForOutput.size());
        writer.append("\r\n\tprivate static func initPolyArray() -> [TzPolygon]\n" +
                "    {\n" +
                "        poly = [TzPolygon]()\n" +
                "    \r\n");
        for (int i=1; i < slab; i++) {
            writer.append("\t\tinit" + i + "()\n");
        }
        writer.append("\t\treturn poly\n" +
                "\t}\n\n");

        writer.append("}\n\n");
        writer.close();
    }
}
