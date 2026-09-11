package co.edu.eci.blueprints.api;

import co.edu.eci.blueprints.model.Blueprint;
import co.edu.eci.blueprints.model.Point;
import co.edu.eci.blueprints.persistence.BlueprintNotFoundException;
import co.edu.eci.blueprints.persistence.BlueprintPersistenceException;
import co.edu.eci.blueprints.services.BlueprintsServices;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/blueprints")
@Tag(name = "Blueprints", description = "Los endpoints del negocio. Todos necesitan token.")
@SecurityRequirement(name = "bearer-jwt")
public class BlueprintController {

    private final BlueprintsServices services;

    public BlueprintController(BlueprintsServices services) { this.services = services; }

    @Operation(summary = "Todos los blueprints", description = "Devuelve la lista completa. Necesita scope blueprints.read.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de blueprints"),
        @ApiResponse(responseCode = "401", description = "Token inválido o ausente"),
        @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<Set<Blueprint>> getAll() {
        return ResponseEntity.ok(services.getAllBlueprints());
    }

    @Operation(summary = "Blueprints de un autor", description = "Filtra por autor. Si no existe devuelve 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Blueprints del autor"),
        @ApiResponse(responseCode = "404", description = "Autor no encontrado"),
        @ApiResponse(responseCode = "401", description = "Token inválido o ausente")
    })
    @GetMapping("/{author}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<?> byAuthor(@PathVariable String author) {
        try {
            return ResponseEntity.ok(services.getBlueprintsByAuthor(author));
        } catch (BlueprintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @Operation(summary = "Un blueprint específico", description = "Busca por autor + nombre. Aplica el filtro activo (identity/redundancy/undersampling).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Blueprint encontrado"),
        @ApiResponse(responseCode = "404", description = "Blueprint no encontrado"),
        @ApiResponse(responseCode = "401", description = "Token inválido o ausente")
    })
    @GetMapping("/{author}/{bpname}")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.read')")
    public ResponseEntity<?> byAuthorAndName(@PathVariable String author, @PathVariable String bpname) {
        try {
            return ResponseEntity.ok(services.getBlueprint(author, bpname));
        } catch (BlueprintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @Operation(summary = "Crear blueprint", description = "Crea uno nuevo. Si ya existe con ese autor+nombre devuelve 409.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Blueprint creado"),
        @ApiResponse(responseCode = "409", description = "Blueprint ya existe"),
        @ApiResponse(responseCode = "401", description = "Token inválido o ausente"),
        @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<?> add(@Valid @RequestBody NewBlueprintRequest req) {
        try {
            services.addNewBlueprint(new Blueprint(req.author(), req.name(), req.points()));
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (BlueprintPersistenceException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @Operation(summary = "Agregar punto", description = "Le mete un punto nuevo a un blueprint existente.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Punto agregado"),
        @ApiResponse(responseCode = "404", description = "Blueprint no encontrado"),
        @ApiResponse(responseCode = "401", description = "Token inválido o ausente"),
        @ApiResponse(responseCode = "403", description = "Sin permiso (scope insuficiente)")
    })
    @PutMapping("/{author}/{bpname}/points")
    @PreAuthorize("hasAuthority('SCOPE_blueprints.write')")
    public ResponseEntity<?> addPoint(@PathVariable String author, @PathVariable String bpname,
                                      @RequestBody Point p) {
        try {
            services.addPoint(author, bpname, p.x(), p.y());
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } catch (BlueprintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    public record NewBlueprintRequest(
            @NotBlank String author,
            @NotBlank String name,
            @Valid List<Point> points
    ) {}
}
