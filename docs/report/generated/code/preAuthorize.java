// com/iitsm/controller/IncidentController.java
@PostMapping("/{id}/close")
@PreAuthorize("hasRole('ADMIN')")
public IncidentDetailResponse close(@PathVariable Long id) {
    return incidents.close(id);
}
