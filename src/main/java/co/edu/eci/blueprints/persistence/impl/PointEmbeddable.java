package co.edu.eci.blueprints.persistence.impl;

import jakarta.persistence.Embeddable;

@Embeddable
public class PointEmbeddable {
    private int x;
    private int y;

    protected PointEmbeddable() {}
    public PointEmbeddable(int x, int y) { this.x = x; this.y = y; }

    public int getX() { return x; }
    public int getY() { return y; }
}
