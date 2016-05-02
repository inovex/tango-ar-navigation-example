package de.stetro.tango.arnavigation.data;


import org.rajawali3d.math.vector.Vector2;

import java.util.ArrayList;
import java.util.List;

public class QuadTree {

    public static final double PLANE_SPACER = 0.02;
    private final Vector2 position;
    private final double halfRange;
    private final int depth;
    private final double range;
    private boolean filled = false;
    private QuadTree[] children = new QuadTree[4];
    private QuadTreeDataListener listener;

    public QuadTree(Vector2 position, double range, int depth) {
        this.position = position;
        this.halfRange = range / 2.0;
        this.depth = depth;
        this.range = range;
    }

    public List<Vector2> getFilledEdgePointsAsPolygon() {
        ArrayList<Vector2> list = new ArrayList<>();
        getFilledEdgePointsAsPolygon(list);
        return list;
    }

    private void getFilledEdgePointsAsPolygon(ArrayList<Vector2> list) {
        if (depth == 0 && filled) {
            list.add(new Vector2(position.getX(), position.getY()));
            list.add(new Vector2(position.getX() + range - PLANE_SPACER, position.getY()));
            list.add(new Vector2(position.getX(), position.getY() + range - PLANE_SPACER));

            list.add(new Vector2(position.getX(), position.getY() + range - PLANE_SPACER));
            list.add(new Vector2(position.getX() + range - PLANE_SPACER, position.getY()));
            list.add(new Vector2(position.getX() + range - PLANE_SPACER, position.getY() + range - PLANE_SPACER));
        } else {
            for (QuadTree child : children) {
                if (child != null) {
                    child.getFilledEdgePointsAsPolygon(list);
                }
            }
        }
    }

    public List<Vector2> getFilledPoints() {
        ArrayList<Vector2> list = new ArrayList<>();
        getFilledPoints(list);
        return list;
    }

    private void getFilledPoints(ArrayList<Vector2> list) {
        if (depth == 0 && filled) {
            list.add(position);
        } else {
            for (QuadTree child : children) {
                if (child != null) {
                    child.getFilledPoints(list);
                }
            }
        }
    }

    public void setFilledInvalidate(Vector2 point) {
        if (!isFilled(point)) {
            setFilled(point);
            if(listener != null){
                listener.OnQuadTreeUpdate();
            }
        }
    }

    public void setListener(QuadTreeDataListener listener) {
        this.listener = listener;
    }

    public void setFilled(Vector2 point) {
        if (depth == 0) {
            filled = true;
        } else {
            int index = getChildIndex(point);
            if (children[index] == null) {
                children[index] = new QuadTree(getChildPositionByIndex(index), halfRange, depth - 1);
            }
            children[index].setFilled(point);
        }
    }

    private Vector2 getChildPositionByIndex(int index) {
        switch (index) {
            case 0:
                return new Vector2(position.getX(), position.getY());
            case 1:
                return new Vector2(position.getX(), position.getY() + halfRange);
            case 2:
                return new Vector2(position.getX() + halfRange, position.getY());
            default:
                return new Vector2(position.getX() + halfRange, position.getY() + halfRange);
        }
    }

    private int getChildIndex(Vector2 point) {
        if (point.getX() < position.getX() + halfRange) {
            if (point.getY() < position.getY() + halfRange) {
                return 0;
            } else {
                return 1;
            }
        } else {
            if (point.getY() < position.getY() + halfRange) {
                return 2;
            } else {
                return 3;
            }
        }
    }

    public void clear() {
        if (depth == 0) {
            filled = false;
        } else {
            for (QuadTree child : children) {
                if (child != null) {
                    child.clear();
                }
            }
        }
    }

    public boolean isFilled(Vector2 to) {
        if (outOfRange(to)) {
            return false;
        } else if (depth == 0) {
            return filled;
        } else {
            int index = getChildIndex(to);
            return children[index] != null && children[index].isFilled(to);
        }
    }

    private boolean outOfRange(Vector2 to) {
        return to.getX() > position.getX() + range ||
                to.getX() < position.getX() ||
                to.getY() > position.getY() + range ||
                to.getY() < position.getY();
    }

    public double getUnit() {
        return range / (Math.pow(2, depth));
    }

    public Vector2 rasterize(Vector2 a) {
        if (depth == 0) {
            return position;
        } else {
            int index = getChildIndex(a);
            if (children[index] != null) {
                return children[index].rasterize(a);
            }
        }
        return a;
    }

    public interface QuadTreeDataListener {

        void OnQuadTreeUpdate();
    }
}
