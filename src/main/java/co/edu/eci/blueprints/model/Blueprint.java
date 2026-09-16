package co.edu.eci.blueprints.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public class Blueprint {

    private final String author;
    private final String name;
    // CopyOnWriteArrayList: varias peticiones pueden agregar puntos al mismo blueprint a la vez
    private final List<Point> points = new CopyOnWriteArrayList<>();

    public Blueprint(String author, String name, List<Point> pts) {
        this.author = author;
        this.name = name;
        if (pts != null) points.addAll(pts);
    }

    public String getAuthor() { return author; }
    public String getName() { return name; }
    public List<Point> getPoints() { return Collections.unmodifiableList(points); }

    public void addPoint(Point p) { points.add(p); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Blueprint bp)) return false;
        return Objects.equals(author, bp.author) && Objects.equals(name, bp.name);
    }

    @Override
    public int hashCode() { return Objects.hash(author, name); }
}
