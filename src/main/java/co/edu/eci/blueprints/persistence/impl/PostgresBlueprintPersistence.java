package co.edu.eci.blueprints.persistence.impl;

import co.edu.eci.blueprints.model.Blueprint;
import co.edu.eci.blueprints.model.Point;
import co.edu.eci.blueprints.persistence.BlueprintNotFoundException;
import co.edu.eci.blueprints.persistence.BlueprintPersistence;
import co.edu.eci.blueprints.persistence.BlueprintPersistenceException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@Profile("postgres")
public class PostgresBlueprintPersistence implements BlueprintPersistence {

    private final BlueprintJpaRepository repo;

    public PostgresBlueprintPersistence(BlueprintJpaRepository repo) { this.repo = repo; }

    private Blueprint toDomain(BlueprintEntity e) {
        List<Point> pts = e.getPoints().stream().map(p -> new Point(p.getX(), p.getY())).toList();
        return new Blueprint(e.getAuthor(), e.getName(), pts);
    }

    private BlueprintEntity toEntity(Blueprint bp) {
        List<PointEmbeddable> pts = bp.getPoints().stream().map(p -> new PointEmbeddable(p.x(), p.y())).toList();
        return new BlueprintEntity(bp.getAuthor(), bp.getName(), pts);
    }

    // Sin @Transactional a proposito: saveAndFlush corre en su propia transaccion, asi la
    // violacion de UNIQUE se puede capturar y devolver como 409 sin dejar la transaccion
    // externa marcada como rollback-only.
    @Override
    public void saveBlueprint(Blueprint bp) throws BlueprintPersistenceException {
        String key = bp.getAuthor() + "/" + bp.getName();
        if (repo.findByAuthorAndName(bp.getAuthor(), bp.getName()).isPresent())
            throw new BlueprintPersistenceException("Blueprint already exists: " + key);
        try {
            repo.saveAndFlush(toEntity(bp));
        } catch (DataIntegrityViolationException e) {
            // Dos POST simultaneos con el mismo autor+nombre: la restriccion UNIQUE decide
            throw new BlueprintPersistenceException("Blueprint already exists: " + key);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Blueprint getBlueprint(String author, String name) throws BlueprintNotFoundException {
        return repo.findByAuthorAndName(author, name)
                .map(this::toDomain)
                .orElseThrow(() -> new BlueprintNotFoundException("Blueprint not found: " + author + "/" + name));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Blueprint> getBlueprintsByAuthor(String author) throws BlueprintNotFoundException {
        List<BlueprintEntity> list = repo.findByAuthor(author);
        if (list.isEmpty()) throw new BlueprintNotFoundException("No blueprints for author: " + author);
        return list.stream().map(this::toDomain).collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Blueprint> getAllBlueprints() {
        return repo.findAll().stream().map(this::toDomain).collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void addPoint(String author, String name, int x, int y) throws BlueprintNotFoundException {
        BlueprintEntity e = repo.findByAuthorAndName(author, name)
                .orElseThrow(() -> new BlueprintNotFoundException("Blueprint not found: " + author + "/" + name));
        e.getPoints().add(new PointEmbeddable(x, y));
    }
}
