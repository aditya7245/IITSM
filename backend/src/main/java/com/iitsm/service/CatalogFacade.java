package com.iitsm.service;

import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.catalog.Skill;
import com.iitsm.domain.sla.SlaPolicy;
import com.iitsm.dto.AdminRequests;
import com.iitsm.dto.AssignmentStrategyResponse;
import com.iitsm.dto.TeamLeadAssignmentResponse;
import com.iitsm.dto.CategoryResponse;
import com.iitsm.dto.ResponseMapper;
import com.iitsm.dto.ServiceResponse;
import com.iitsm.dto.SkillResponse;
import com.iitsm.dto.SlaPolicyResponse;
import com.iitsm.exception.DuplicateResourceException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.ResourceNotFoundException;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ITServiceRepository;
import com.iitsm.repository.SkillRepository;
import com.iitsm.repository.SlaPolicyRepository;
import com.iitsm.strategy.AssignmentStrategy;
import com.iitsm.strategy.SkillBasedStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * The lists everyone reads (services, categories, skills) and the configuration an administrator manages: IT services,
 * categories, SLA times and the active assignment strategy. Services and categories are never deleted, only switched
 * off, because incidents refer to them. Who may call the administration methods is declared on the controller.
 */
@Service
@Transactional
public class CatalogFacade {

    private final ITServiceRepository services;
    private final CategoryRepository categories;
    private final SkillRepository skills;
    private final SlaPolicyRepository slaPolicies;
    private final ConfigService config;
    private final List<AssignmentStrategy> strategies;
    private final ResponseMapper mapper;

    public CatalogFacade(ITServiceRepository services, CategoryRepository categories, SkillRepository skills,
                         SlaPolicyRepository slaPolicies, ConfigService config, List<AssignmentStrategy> strategies,
                         ResponseMapper mapper) {
        this.services = services;
        this.categories = categories;
        this.skills = skills;
        this.slaPolicies = slaPolicies;
        this.config = config;
        this.strategies = strategies;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ for everyone

    /** The services that can be reported against (active ones), by name. */
    @Transactional(readOnly = true)
    public List<ServiceResponse> activeServices() {
        return services.findByActiveTrue().stream().sorted(byName(ITService::getName)).map(mapper::toService).toList();
    }

    /** The categories that can be chosen (active ones), by name. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> activeCategories() {
        return categories.findByActiveTrue().stream().sorted(byName(Category::getName)).map(mapper::toCategory).toList();
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> skills() {
        return skills.findAll().stream().sorted(byName(Skill::getName)).map(mapper::toSkill).toList();
    }

    // ------------------------------------------------------------------ IT services

    /** All services including the switched-off ones. */
    @Transactional(readOnly = true)
    public List<ServiceResponse> allServices() {
        return services.findAll().stream().sorted(byName(ITService::getName)).map(mapper::toService).toList();
    }

    public ServiceResponse createService(AdminRequests.ServiceRequest request) {
        String name = request.name().strip();
        if (services.findByNameIgnoreCase(name).isPresent()) {
            throw new DuplicateResourceException("A service named '" + name + "' already exists");
        }
        ITService service = new ITService(name, cleaned(request.description()), request.businessCriticality().shortValue());
        if (Boolean.FALSE.equals(request.active())) {
            service.deactivate();
        }
        return mapper.toService(services.save(service));
    }

    public ServiceResponse updateService(Long id, AdminRequests.ServiceRequest request) {
        ITService service = services.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service " + id + " not found"));
        String name = request.name().strip();
        services.findByNameIgnoreCase(name).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new DuplicateResourceException("A service named '" + name + "' already exists");
        });
        service.update(name, cleaned(request.description()), request.businessCriticality().shortValue());
        if (request.active() != null) {
            if (request.active()) {
                service.activate();
            } else {
                service.deactivate();
            }
        }
        return mapper.toService(service);
    }

    // ------------------------------------------------------------------ categories

    /** All categories including the switched-off ones. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> allCategories() {
        return categories.findAll().stream().sorted(byName(Category::getName)).map(mapper::toCategory).toList();
    }

    public CategoryResponse createCategory(AdminRequests.CategoryRequest request) {
        String name = request.name().strip();
        if (categories.findByNameIgnoreCase(name).isPresent()) {
            throw new DuplicateResourceException("A category named '" + name + "' already exists");
        }
        Category category = new Category(name, cleaned(request.description()), cleanKeywords(request.keywords()),
                skillOrNull(request.requiredSkillId()));
        if (Boolean.FALSE.equals(request.active())) {
            category.deactivate();
        }
        return mapper.toCategory(categories.save(category));
    }

    public CategoryResponse updateCategory(Long id, AdminRequests.CategoryRequest request) {
        Category category = categories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
        String name = request.name().strip();
        categories.findByNameIgnoreCase(name).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new DuplicateResourceException("A category named '" + name + "' already exists");
        });
        category.update(name, cleaned(request.description()), cleanKeywords(request.keywords()),
                skillOrNull(request.requiredSkillId()));
        if (request.active() != null) {
            if (request.active()) {
                category.activate();
            } else {
                category.deactivate();
            }
        }
        return mapper.toCategory(category);
    }

    // ------------------------------------------------------------------ SLA policies

    /** The default policies first (by priority), then the overrides of single services. */
    @Transactional(readOnly = true)
    public List<SlaPolicyResponse> slaPolicies() {
        Comparator<SlaPolicy> order = Comparator
                .comparing((SlaPolicy p) -> p.getService() == null ? "" : p.getService().getName().toLowerCase())
                .thenComparing(SlaPolicy::getPriority);
        return slaPolicies.findAll().stream().sorted(order).map(mapper::toSlaPolicy).toList();
    }

    /** A priority and service pair can have only one policy (one default per priority, one override per service). */
    public SlaPolicyResponse createSlaPolicy(AdminRequests.CreateSlaPolicy request) {
        ITService service = request.serviceId() == null ? null
                : services.findById(request.serviceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Service " + request.serviceId() + " not found"));
        boolean exists = service == null
                ? slaPolicies.findByPriorityAndServiceIsNull(request.priority()).isPresent()
                : slaPolicies.findByPriorityAndServiceId(request.priority(), service.getId()).isPresent();
        if (exists) {
            throw new DuplicateResourceException(service == null
                    ? "The default policy for " + request.priority() + " already exists"
                    : "A policy for " + request.priority() + " on '" + service.getName() + "' already exists");
        }
        return mapper.toSlaPolicy(slaPolicies.save(new SlaPolicy(request.priority(), service,
                request.responseMinutes(), request.resolutionMinutes())));
    }

    /** Changes the times only: the priority and the service are what identify the policy. */
    public SlaPolicyResponse updateSlaPolicy(Long id, AdminRequests.UpdateSlaPolicy request) {
        SlaPolicy policy = slaPolicy(id);
        policy.changeMinutes(request.responseMinutes(), request.resolutionMinutes());
        return mapper.toSlaPolicy(policy);
    }

    /** Removes the override of one service. The default policy of a priority cannot be removed (edit it instead). */
    public void deleteSlaPolicy(Long id) {
        SlaPolicy policy = slaPolicy(id);
        if (policy.getService() == null) {
            throw new InvalidInputException("The default policy for " + policy.getPriority()
                    + " cannot be deleted; change its times instead");
        }
        slaPolicies.delete(policy);
    }

    // ------------------------------------------------------------------ assignment strategy

    @Transactional(readOnly = true)
    public AssignmentStrategyResponse assignmentStrategy() {
        return new AssignmentStrategyResponse(config.getString(ConfigKeys.ASSIGNMENT_STRATEGY, SkillBasedStrategy.NAME),
                strategyNames());
    }

    /** Chooses the strategy used for the next automatic assignment. It must be one of the available names. */
    public AssignmentStrategyResponse setAssignmentStrategy(String strategy) {
        List<String> names = strategyNames();
        if (!names.contains(strategy)) {
            throw new InvalidInputException("Unknown strategy '" + strategy + "'. Choose one of: " + String.join(", ", names));
        }
        config.set(ConfigKeys.ASSIGNMENT_STRATEGY, strategy);
        return new AssignmentStrategyResponse(strategy, names);
    }

    // ------------------------------------------------------------------ team leads in automatic assignment

    @Transactional(readOnly = true)
    public TeamLeadAssignmentResponse teamLeadAssignment() {
        return new TeamLeadAssignmentResponse(config.getBoolean(ConfigKeys.AUTO_ASSIGN_TEAM_LEADS, false));
    }

    /** Switches on or off that team leads are given new incidents automatically. It applies from the next assignment. */
    public TeamLeadAssignmentResponse setTeamLeadAssignment(boolean enabled) {
        config.set(ConfigKeys.AUTO_ASSIGN_TEAM_LEADS, Boolean.toString(enabled));
        return new TeamLeadAssignmentResponse(enabled);
    }

    // ------------------------------------------------------------------ helpers

    private List<String> strategyNames() {
        return strategies.stream().map(AssignmentStrategy::name).sorted().toList();
    }

    private SlaPolicy slaPolicy(Long id) {
        return slaPolicies.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA policy " + id + " not found"));
    }

    private Skill skillOrNull(Long skillId) {
        return skillId == null ? null
                : skills.findById(skillId).orElseThrow(() -> new ResourceNotFoundException("Skill " + skillId + " not found"));
    }

    private static <T> Comparator<T> byName(java.util.function.Function<T, String> name) {
        return Comparator.comparing(name, String.CASE_INSENSITIVE_ORDER);
    }

    private static String cleaned(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /** Keywords are stored as a comma-separated list without stray spaces or empty entries. */
    private static String cleanKeywords(String keywords) {
        if (keywords == null) {
            return "";
        }
        return java.util.Arrays.stream(keywords.split(","))
                .map(String::strip)
                .filter(word -> !word.isEmpty())
                .collect(java.util.stream.Collectors.joining(","));
    }
}
