package co.edu.eci.blueprints;

import co.edu.eci.blueprints.filters.IdentityFilter;
import co.edu.eci.blueprints.filters.RedundancyFilter;
import co.edu.eci.blueprints.filters.UndersamplingFilter;
import co.edu.eci.blueprints.model.Blueprint;
import co.edu.eci.blueprints.model.Point;
import co.edu.eci.blueprints.persistence.BlueprintNotFoundException;
import co.edu.eci.blueprints.persistence.BlueprintPersistenceException;
import co.edu.eci.blueprints.persistence.InMemoryBlueprintPersistence;
import co.edu.eci.blueprints.services.BlueprintsServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas unitarias de la logica de negocio y los filtros (heredadas del P1). */
class BlueprintsServicesTest {

    private BlueprintsServices services;
    private InMemoryBlueprintPersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new InMemoryBlueprintPersistence();
        services = new BlueprintsServices(persistence, new IdentityFilter());
    }

    @Test
    void getAllBlueprintsReturnsPreloadedData() {
        assertEquals(3, services.getAllBlueprints().size());
    }

    @Test
    void getBlueprintsByAuthorReturnsOnlyThatAuthor() throws BlueprintNotFoundException {
        var bps = services.getBlueprintsByAuthor("john");
        assertEquals(2, bps.size());
        assertTrue(bps.stream().allMatch(b -> b.getAuthor().equals("john")));
    }

    @Test
    void getBlueprintsByUnknownAuthorThrows() {
        assertThrows(BlueprintNotFoundException.class, () -> services.getBlueprintsByAuthor("nobody"));
    }

    @Test
    void getUnknownBlueprintThrows() {
        assertThrows(BlueprintNotFoundException.class, () -> services.getBlueprint("john", "nothing"));
    }

    @Test
    void addNewBlueprintAndRetrieveIt() throws Exception {
        services.addNewBlueprint(new Blueprint("alice", "tower", List.of(new Point(1, 2))));
        Blueprint found = services.getBlueprint("alice", "tower");
        assertEquals("alice", found.getAuthor());
        assertEquals(List.of(new Point(1, 2)), found.getPoints());
    }

    @Test
    void addDuplicateBlueprintThrows() throws Exception {
        Blueprint bp = new Blueprint("bob", "shed", List.of(new Point(0, 0)));
        services.addNewBlueprint(bp);
        assertThrows(BlueprintPersistenceException.class, () -> services.addNewBlueprint(bp));
    }

    @Test
    void addPointAppendsAtTheEnd() throws Exception {
        int before = services.getBlueprint("john", "house").getPoints().size();
        services.addPoint("john", "house", 99, 99);
        List<Point> after = persistence.getBlueprint("john", "house").getPoints();
        assertEquals(before + 1, after.size());
        assertEquals(new Point(99, 99), after.get(after.size() - 1));
    }

    @Test
    void addPointToUnknownBlueprintThrows() {
        assertThrows(BlueprintNotFoundException.class, () -> services.addPoint("nobody", "x", 1, 1));
    }

    @Test
    void redundancyFilterRemovesConsecutiveDuplicates() {
        Blueprint bp = new Blueprint("x", "y", List.of(
                new Point(1, 1), new Point(1, 1), new Point(2, 2), new Point(1, 1)));
        Blueprint filtered = new RedundancyFilter().apply(bp);
        assertEquals(List.of(new Point(1, 1), new Point(2, 2), new Point(1, 1)), filtered.getPoints());
    }

    @Test
    void undersamplingFilterKeepsEvenIndexes() {
        Blueprint bp = new Blueprint("x", "y", List.of(
                new Point(0, 0), new Point(1, 1), new Point(2, 2), new Point(3, 3)));
        Blueprint filtered = new UndersamplingFilter().apply(bp);
        assertEquals(List.of(new Point(0, 0), new Point(2, 2)), filtered.getPoints());
    }

    @Test
    void serviceAppliesConfiguredFilterOnlyOnSingleBlueprint() throws Exception {
        BlueprintsServices withFilter = new BlueprintsServices(persistence, new UndersamplingFilter());
        assertEquals(2, withFilter.getBlueprint("john", "house").getPoints().size());
        assertEquals(4, persistence.getBlueprint("john", "house").getPoints().size());
    }
}
