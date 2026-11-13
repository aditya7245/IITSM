package com.iitsm.controller;

import com.iitsm.dto.CategoryResponse;
import com.iitsm.dto.ServiceResponse;
import com.iitsm.dto.SkillResponse;
import com.iitsm.service.CatalogFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The lists any logged-in user needs, for example to fill in the report form. */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogFacade catalog;

    public CatalogController(CatalogFacade catalog) {
        this.catalog = catalog;
    }

    /** Active services only. */
    @GetMapping("/services")
    public List<ServiceResponse> services() {
        return catalog.activeServices();
    }

    /** Active categories only. */
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.activeCategories();
    }

    @GetMapping("/skills")
    public List<SkillResponse> skills() {
        return catalog.skills();
    }
}
