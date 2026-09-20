package co.edu.eci.blueprints.api;

import co.edu.eci.blueprints.model.Blueprint;
import co.edu.eci.blueprints.model.Point;
import co.edu.eci.blueprints.persistence.BlueprintNotFoundException;
import co.edu.eci.blueprints.persistence.BlueprintPersistenceException;
import co.edu.eci.blueprints.services.BlueprintsServices;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/*
 * Nota: las anotaciones de Swagger se escriben con su nombre completo
 * (io.swagger...ApiResponse) porque chocan con nuestro record ApiResponse<T>.
 */
@RestController
@RequestMapping("/api/v1/blueprints")
@Tag(name = "Blueprints", description = "Endpoints de negocio. Todos requieren un JWT valido.")
@SecurityRequirement(name = "bearer-jwt")
public class BlueprintController {

    private final BlueprintsServices services;

    public BlueprintController(BlueprintsServices services) { this.services = services; }

    @Operation(summary = "Listar todos los blueprints", description = "Requiere scope blueprints.read.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de blueprints"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalido, expirado o ausente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scope insuficiente")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<ApiResponse<Set<Blueprint>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(200, "execute ok", services.getAllBlueprints()));
    }

    @Operation(summary = "Blueprints de un autor", description = "Requiere scope blueprints.read. 404 si el autor no tiene blueprints.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Blueprints del autor"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalido, expirado o ausente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scope insuficiente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Autor sin blueprints")
    })
    @GetMapping("/{author}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<ApiResponse<Set<Blueprint>>> byAuthor(@PathVariable String author)
            throws BlueprintNotFoundException {
        return ResponseEntity.ok(ApiResponse.of(200, "execute ok", services.getBlueprintsByAuthor(author)));
    }

    @Operation(summary = "Blueprint por autor y nombre",
               description = "Requiere scope blueprints.read. Aplica el filtro activo (identity / redundancy / undersampling).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Blueprint encontrado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalido, expirado o ausente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scope insuficiente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint no encontrado")
    })
    @GetMapping("/{author}/{bpname}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<ApiResponse<Blueprint>> byAuthorAndName(@PathVariable String author,
                                                                  @PathVariable String bpname)
            throws BlueprintNotFoundException {
        return ResponseEntity.ok(ApiResponse.of(200, "execute ok", services.getBlueprint(author, bpname)));
    }

    @Operation(summary = "Crear blueprint", description = "Requiere scope blueprints.write. 409 si ya existe autor+nombre.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Blueprint creado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos invalidos"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalido, expirado o ausente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scope insuficiente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "El blueprint ya existe")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<ApiResponse<Blueprint>> add(@Valid @RequestBody NewBlueprintRequest req)
            throws BlueprintPersistenceException {
        Blueprint bp = new Blueprint(req.author(), req.name(), req.points());
        services.addNewBlueprint(bp);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(201, "created", bp));
    }

    @Operation(summary = "Agregar un punto", description = "Requiere scope blueprints.write.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Punto agregado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Punto invalido"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalido, expirado o ausente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scope insuficiente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint no encontrado")
    })
    @PutMapping("/{author}/{bpname}/points")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<ApiResponse<Void>> addPoint(@PathVariable String author,
                                                      @PathVariable String bpname,
                                                      @Valid @RequestBody PointRequest p)
            throws BlueprintNotFoundException {
        services.addPoint(author, bpname, p.x(), p.y());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.of(202, "accepted"));
    }

    @Operation(summary = "Actualizar blueprint",
               description = "Requiere scope blueprints.write. Reemplaza todos los puntos del blueprint. "
                           + "author/name en el cuerpo son opcionales; si vienen deben coincidir con la URL (no se permite renombrar).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Blueprint actualizado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Datos invalidos o author/name distintos a la URL"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalido, expirado o ausente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scope insuficiente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint no encontrado")
    })
    @PutMapping("/{author}/{bpname}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<ApiResponse<Blueprint>> update(@PathVariable String author,
                                                         @PathVariable String bpname,
                                                         @Valid @RequestBody UpdateBlueprintRequest req)
            throws BlueprintNotFoundException {
        boolean authorMismatch = req.author() != null && !req.author().equals(author);
        boolean nameMismatch = req.name() != null && !req.name().equals(bpname);
        if (authorMismatch || nameMismatch) {
            return ResponseEntity.badRequest().body(ApiResponse.<Blueprint>of(400,
                    "author/name in the body must match the URL (renaming is not supported)", null));
        }
        Blueprint updated = services.updateBlueprint(author, bpname, req.points());
        return ResponseEntity.ok(ApiResponse.of(200, "updated", updated));
    }

    @Operation(summary = "Eliminar blueprint", description = "Requiere scope blueprints.write.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Blueprint eliminado"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalido, expirado o ausente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Scope insuficiente"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Blueprint no encontrado")
    })
    @DeleteMapping("/{author}/{bpname}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String author,
                                                    @PathVariable String bpname)
            throws BlueprintNotFoundException {
        services.deleteBlueprint(author, bpname);
        return ResponseEntity.ok(ApiResponse.of(200, "deleted"));
    }

    public record NewBlueprintRequest(
            @NotBlank String author,
            @NotBlank String name,
            List<Point> points
    ) {}

    /** Cuerpo del PUT completo: la lista de puntos es obligatoria (puede ir vacia). */
    public record UpdateBlueprintRequest(
            String author,
            String name,
            @NotNull List<@NotNull Point> points
    ) {}

    /** Integer (no int) para que un JSON sin x o y responda 400 en vez de asumir 0. */
    public record PointRequest(@NotNull Integer x, @NotNull Integer y) {}
}
