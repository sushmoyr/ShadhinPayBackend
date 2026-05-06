# Spring Boot — UseCase + Hexagonal Architecture Guidelines

- **Purpose:** Strict architecture reference for Spring Boot backend projects.
- **Pattern:** UseCase-driven Hexagonal Architecture (Ports & Adapters) with feature-based vertical slicing.
- **Stack:** Spring Boot 3.x, Spring Data JPA, Spring Security, PostgreSQL, MapStruct, Lombok.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Package Structure](#2-package-structure)
3. [Feature Module Anatomy](#3-feature-module-anatomy)
4. [Use Cases](#4-use-cases)
5. [Controllers (Inbound Adapters)](#5-controllers-inbound-adapters)
6. [Repositories (Outbound Adapters)](#6-repositories-outbound-adapters)
7. [Entities & Domain Model](#7-entities--domain-model)
8. [DTOs (Data Transfer Objects)](#8-dtos-data-transfer-objects)
9. [Mappers](#9-mappers)
10. [API Response Envelope](#10-api-response-envelope)
11. [Exception Hierarchy](#11-exception-hierarchy)
12. [Validation](#12-validation)
13. [Route Constants](#13-route-constants)
14. [JPA Specifications (Filtering)](#14-jpa-specifications-filtering)
15. [Pagination](#15-pagination)
16. [Domain Events](#16-domain-events)
17. [Security & Authorization](#17-security--authorization)
18. [Auditing](#18-auditing)
19. [Configuration](#19-configuration)
20. [Custom Annotations](#20-custom-annotations)
21. [Dependency Rules](#21-dependency-rules)
22. [Naming Conventions Summary](#22-naming-conventions-summary)

---

## 1. Architecture Overview

### Layers

```
┌────────────────────────────────────────────────────┐
│              PRESENTATION LAYER                    │
│   Controllers (REST Adapters / Inbound Ports)      │
│   Receives HTTP → delegates to UseCases            │
└──────────────────────┬─────────────────────────────┘
                       │ depends on (interface)
┌──────────────────────▼─────────────────────────────┐
│             APPLICATION LAYER                      │
│   UseCase interfaces + implementations             │
│   Orchestrates domain logic, calls repositories    │
└──────────────────────┬─────────────────────────────┘
                       │ depends on (interface)
┌──────────────────────▼─────────────────────────────┐
│               DOMAIN LAYER                         │
│   Entities, Enums, Exceptions, Events              │
│   Repository interfaces (Outbound Ports)           │
│   Zero framework dependencies in pure form         │
└──────────────────────┬─────────────────────────────┘
                       │ implemented by
┌──────────────────────▼─────────────────────────────┐
│           INFRASTRUCTURE LAYER                     │
│   JPA Repository implementations (auto by Spring)  │
│   External service adapters (SMS, Payment, Email)  │
│   Configuration, Security filters                  │
└────────────────────────────────────────────────────┘
```

### Dependency Direction (Strict)

- Controllers → UseCase **interfaces** (never implementations)
- UseCases → Repository **interfaces** (never implementations)
- Domain layer depends on **nothing** from infrastructure
- Infrastructure implements domain interfaces
- Dependencies always point **inward** toward the domain

---

## 2. Package Structure

```
com.example.app/
│
├── application/                  # Shared application infrastructure
│   ├── config/                   # Spring @Configuration classes
│   ├── dto/                      # Shared DTOs (ApiResult, pagination)
│   ├── entity/                   # Base entity classes (Auditable)
│   ├── handler/                  # Global exception handler
│   ├── repository/               # Base repository interfaces
│   └── security/                 # Security filters, JWT, auth config
│
├── core/                         # Cross-cutting domain concerns
│   ├── annotation/               # Custom annotations (@UseCase, etc.)
│   ├── constant/                 # Route constants, error codes
│   ├── event/                    # Domain event base types
│   ├── exception/                # Exception hierarchy
│   ├── helper/                   # Pure utility classes
│   └── validator/                # Custom Bean Validation validators
│
├── feature/                      # Business features (vertical slices)
│   ├── product/
│   │   ├── controller/
│   │   ├── usecase/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── dto/
│   │   ├── mapper/
│   │   ├── spec/
│   │   └── ...
│   ├── order/
│   │   └── ... (same sub-packages)
│   ├── category/
│   ├── customer/
│   └── ...
│
└── library/                      # External service adapters
    ├── email/                    # Email provider adapter
    ├── sms/                      # SMS provider adapter
    ├── storage/                  # File storage adapter (S3, local)
    └── payment/                  # Payment gateway adapter
```

### Rules

- **`application/`** — Framework plumbing shared across all features. No business logic.
- **`core/`** — Domain-level types reused across features: exceptions, annotations, constants, validators. No Spring
  `@Service` or `@Repository` beans here.
- **`feature/`** — Each subdirectory is a self-contained vertical slice. All layers for a feature live together.
- **`library/`** — Adapters for external systems. Each external integration is isolated in its own package with its own
  DTOs, exceptions, and config.

---

## 3. Feature Module Anatomy

Every feature follows this internal structure:

```
feature/{name}/
├── controller/
│   ├── {Name}Controller.java            # Interface (port definition)
│   └── {Name}ControllerImpl.java        # Implementation (REST adapter)
├── usecase/
│   ├── Create{Name}UseCase.java         # Interface
│   ├── Create{Name}UseCaseImpl.java     # Implementation
│   ├── Get{Name}UseCase.java
│   ├── Get{Name}UseCaseImpl.java
│   ├── Update{Name}UseCase.java
│   ├── Update{Name}UseCaseImpl.java
│   ├── Delete{Name}UseCase.java
│   └── Delete{Name}UseCaseImpl.java
├── repository/
│   └── {Name}Repository.java            # Spring Data JPA interface
├── entity/
│   ├── {Name}.java                      # JPA entity
│   └── {Name}Status.java                # Enum (if applicable)
├── dto/
│   ├── Create{Name}Request.java         # Inbound DTO
│   ├── Update{Name}Request.java         # Inbound DTO
│   ├── {Name}Dto.java                   # Outbound DTO (detail)
│   └── {Name}SummaryDto.java            # Outbound DTO (list item)
├── mapper/
│   └── {Name}Mapper.java                # MapStruct mapper
├── spec/
│   └── {Name}Spec.java                  # JPA Specification for filtering
└── validator/                            # Feature-specific validators (optional)
```

### Rules

- Every sub-package is optional. A simple feature may only need `controller/`, `usecase/`, `entity/`, `dto/`.
- No feature may import another feature's `entity/` directly. Cross-feature communication happens through use case
  interfaces or domain events.
- If a feature needs data from another feature, inject that feature's **use case interface**, not its repository.

---

## 4. Use Cases

A use case encapsulates a **single application-level business operation**.

### Interface (Port)

```java
public interface CreateProductUseCase {
    ProductDto execute(CreateProductRequest request);
}
```

### Implementation

```java

@UseCase
@RequiredArgsConstructor
public class CreateProductUseCaseImpl implements CreateProductUseCase {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper mapper;

    @Override
    @Transactional
    public ProductDto execute(CreateProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId()));

        Product product = mapper.toEntity(request);
        product.setCategory(category);

        Product saved = productRepository.save(product);
        return mapper.toDto(saved);
    }
}
```

### Rules

| Rule                  | Detail                                                         |
|-----------------------|----------------------------------------------------------------|
| Annotation            | Always `@UseCase` (custom, extends `@Component`)               |
| Constructor injection | Via `@RequiredArgsConstructor` — no field injection            |
| Method name           | Always `execute(...)`                                          |
| Input                 | A request DTO or primitive IDs — never a raw entity            |
| Output                | A response DTO, `void`, or a primitive — never a raw entity    |
| Transactions          | `@Transactional` on the `execute` method, not the class        |
| One operation         | One use case = one business action. No multi-purpose use cases |
| Naming                | `{Verb}{Entity}UseCase` / `{Verb}{Entity}UseCaseImpl`          |

### Use Case Granularity

```
// ✅ Correct — granular, single-purpose
CreateProductUseCase
GetProductByIdUseCase
ListProductsUseCase
UpdateProductUseCase
DeleteProductUseCase
SearchProductsUseCase

// ❌ Wrong — bloated, multi-purpose
ProductUseCase          // Too vague
ProductCRUDUseCase      // Multiple operations
ManageProductsUseCase   // God use case
```

### Cross-Feature Calls

```java

@UseCase
@RequiredArgsConstructor
public class PlaceOrderUseCaseImpl implements PlaceOrderUseCase {

    private final OrderRepository orderRepository;
    private final GetProductByIdUseCase getProductUseCase;  // ✅ Inject use case interface
    // private final ProductRepository productRepository;   // ❌ Don't reach into other feature's repo

    @Override
    @Transactional
    public OrderDto execute(PlaceOrderRequest request) {
        ProductDto product = getProductUseCase.execute(request.getProductId());
        // ... build order from product info
    }
}
```

---

## 5. Controllers (Inbound Adapters)

Controllers are **port definitions** (interfaces) with **adapter implementations**.

### Interface (Port)

```java

@RequestMapping(Routes.V1.Admin.Product.BASE)
@Tag(name = "Admin - Products")
public interface AdminProductController {

    @PostMapping
    ResponseEntity<ApiResult<ProductDto>> create(CreateProductRequest body);

    @GetMapping
    ResponseEntity<ApiResult<List<ProductSummaryDto>>> list(
            int page, int size, String sortBy, Sort.Direction order, boolean paginate, String search);

    @GetMapping("/{id}")
    ResponseEntity<ApiResult<ProductDto>> getById(@PathVariable Long id);

    @PutMapping("/{id}")
    ResponseEntity<ApiResult<ProductDto>> update(@PathVariable Long id, UpdateProductRequest body);

    @DeleteMapping("/{id}")
    ResponseEntity<ApiResult<Void>> delete(@PathVariable Long id);
}
```

### Implementation (Adapter)

```java

@RestController
@RequiredArgsConstructor
public class AdminProductControllerImpl implements AdminProductController {

    private final CreateProductUseCase createProductUseCase;
    private final ListProductsUseCase listProductsUseCase;
    private final GetProductByIdUseCase getProductByIdUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;

    @Override
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<ApiResult<ProductDto>> create(
            @RequestBody @Valid CreateProductRequest body) {
        return ApiResult.created(createProductUseCase.execute(body));
    }

    @Override
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<ApiResult<List<ProductSummaryDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction order,
            @RequestParam(defaultValue = "true") boolean paginate,
            @RequestParam(required = false) String search) {
        PaginationRequest pagination = new PaginationRequest(page, size, sortBy, order, paginate);
        ProductSpec spec = new ProductSpec(search);
        return ApiResult.ok(listProductsUseCase.execute(pagination, spec));
    }

    @Override
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ResponseEntity<ApiResult<ProductDto>> getById(@PathVariable Long id) {
        return ApiResult.ok(getProductByIdUseCase.execute(id));
    }

    @Override
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ResponseEntity<ApiResult<ProductDto>> update(
            @PathVariable Long id, @RequestBody @Valid UpdateProductRequest body) {
        return ApiResult.ok(updateProductUseCase.execute(id, body));
    }

    @Override
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ResponseEntity<ApiResult<Void>> delete(@PathVariable Long id) {
        deleteProductUseCase.execute(id);
        return ApiResult.ok();
    }
}
```

### Rules

| Rule                             | Detail                                                                                                                        |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| Annotations go on impl           | `@RequestBody`, `@Valid`, `@PathVariable`, `@RequestParam`, `@PreAuthorize` live on the **implementation**, not the interface |
| Mapping annotations on interface | `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc. go on the **interface**                                                |
| No business logic                | Controllers do zero logic — only delegate to use cases and wrap responses                                                     |
| Each use case gets its own field | One field per injected use case. No service facades                                                                           |
| Response wrapping                | Always return `ResponseEntity<ApiResult<T>>`                                                                                  |
| Audience-separated controllers   | `AdminProductController` vs `PublicProductController` for different access levels                                             |

---

## 6. Repositories (Outbound Adapters)

### Standard Repository

```java

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlugAndDeletedFalse(String slug);

    boolean existsByNameAndDeletedFalse(String name);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND p.category.id = :categoryId")
    List<Product> findByCategoryId(@Param("categoryId") Long categoryId);
}
```

### Soft-Delete-Aware Base Repository

```java

@NoRepositoryBean
public interface SoftDeleteRepository<T, ID> extends JpaRepository<T, ID> {

    @Override
    @Query("SELECT e FROM #{#entityName} e WHERE e.deleted = false")
    List<T> findAll();

    @Override
    @Query("SELECT e FROM #{#entityName} e WHERE e.id = :id AND e.deleted = false")
    Optional<T> findById(@Param("id") ID id);

    @Modifying
    @Query("UPDATE #{#entityName} e SET e.deleted = true WHERE e.id = :id")
    void softDeleteById(@Param("id") ID id);
}
```

### Rules

- Repositories are **interfaces only** — Spring Data JPA generates implementations.
- Always include `JpaSpecificationExecutor<T>` for dynamic filtering.
- Suffix query methods with `AndDeletedFalse` when soft delete is used.
- Custom queries use JPQL, never native SQL unless necessary.
- Repository interfaces live in `feature/{name}/repository/`.

---

## 7. Entities & Domain Model

### Base Entity

```java

@MappedSuperclass
@Getter
@Setter
public abstract class Auditable {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

### Soft-Deletable Base

```java

@MappedSuperclass
@Getter
@Setter
public abstract class AuditableAndSoftDeletable extends Auditable {

    @Column(name = "deleted", nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false;
}
```

### Feature Entity

```java

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_product_slug", columnList = "slug"),
        @Index(name = "idx_product_category", columnList = "category_id"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product extends AuditableAndSoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_id_seq")
    @SequenceGenerator(name = "product_id_seq", sequenceName = "product_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ProductImage> images = new ArrayList<>();

    // Domain logic on the entity
    @Transient
    public boolean isInStock() {
        return this.stock != null && this.stock > 0;
    }

    public void decrementStock(int quantity) {
        if (this.stock < quantity) {
            throw new InsufficientStockException("Product", this.id, quantity, this.stock);
        }
        this.stock -= quantity;
    }
}
```

### Rules

| Rule                   | Detail                                                              |
|------------------------|---------------------------------------------------------------------|
| Inheritance            | All entities extend `Auditable` or `AuditableAndSoftDeletable`      |
| ID strategy            | `GenerationType.SEQUENCE` with named sequence generators            |
| Enums                  | Always `@Enumerated(EnumType.STRING)` — never `ORDINAL`             |
| Lazy loading           | `@ManyToOne(fetch = FetchType.LAZY)` by default                     |
| Indexes                | Declare indexes on `@Table` for queried columns                     |
| Domain logic           | Simple domain rules go on the entity via `@Transient` methods       |
| Lombok                 | `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`            |
| No `@Data` on entities | `@Data` generates `equals`/`hashCode` that break JPA proxy behavior |

---

## 8. DTOs (Data Transfer Objects)

### Request DTO (Inbound)

```java

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotNull(message = "Stock is required")
    @PositiveOrZero(message = "Stock cannot be negative")
    private Integer stock;

    @NotNull(message = "Category is required")
    private Long categoryId;

    private String description;
}
```

### Response DTO (Outbound — Detail)

```java

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDto {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String status;
    private CategorySummaryDto category;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Response DTO (Outbound — Summary/List)

```java

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductSummaryDto {
    private Long id;
    private String name;
    private String slug;
    private BigDecimal price;
    private String thumbnailUrl;
    private String status;
    private String categoryName;
}
```

### Naming Rules

| DTO Type          | Naming Pattern          | Example                                  |
|-------------------|-------------------------|------------------------------------------|
| Create request    | `Create{Entity}Request` | `CreateProductRequest`                   |
| Update request    | `Update{Entity}Request` | `UpdateProductRequest`                   |
| Detail response   | `{Entity}Dto`           | `ProductDto`                             |
| Summary/list item | `{Entity}SummaryDto`    | `ProductSummaryDto`                      |
| Nested embed      | `{Entity}SummaryDto`    | `CategorySummaryDto` inside `ProductDto` |

### Rules

- Request DTOs carry Jakarta validation annotations.
- Response DTOs use `@JsonInclude(NON_NULL)` to omit nulls.
- Use `@Accessors(chain = true)` for fluent setters in response DTOs.
- DTOs live in `feature/{name}/dto/` — never in `application/dto/` (that's only for shared infrastructure DTOs like
  `ApiResult`).
- DTOs are **plain data carriers** — no business logic.

---

## 9. Mappers

Use **MapStruct** for entity ↔ DTO mapping.

```java

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public abstract class ProductMapper {

    public abstract Product toEntity(CreateProductRequest request);

    public abstract ProductDto toDto(Product entity);

    public abstract ProductSummaryDto toSummaryDto(Product entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateEntityFromRequest(UpdateProductRequest request, @MappingTarget Product entity);

    protected String mapImageUrl(ProductImage image) {
        return image != null ? image.getUrl() : null;
    }
}
```

### Rules

- `componentModel = "spring"` — always. MapStruct generates a Spring `@Component`.
- `unmappedTargetPolicy = IGNORE` — prevents errors for fields intentionally skipped.
- Use `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)` for partial updates (PATCH).
- Use `abstract class` (not interface) when the mapper needs injected dependencies or custom methods.
- One mapper per feature: `{Entity}Mapper`.
- Mapper lives in `feature/{name}/mapper/`.

---

## 10. API Response Envelope

All endpoints return a consistent response wrapper.

### Structure

```java

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResult<T> {
    private T data;
    private ApiResultMeta meta;
    private PaginationInfo pagination;

    // --- Factory methods ---

    public static <T> ResponseEntity<ApiResult<T>> ok(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setData(data);
        result.setMeta(ApiResultMeta.success());
        return ResponseEntity.ok(result);
    }

    public static <T> ResponseEntity<ApiResult<T>> ok(Page<T> page) {
        ApiResult<T> result = new ApiResult<>();
        result.setData((T) page.getContent());
        result.setMeta(ApiResultMeta.success());
        result.setPagination(PaginationInfo.from(page));
        return ResponseEntity.ok(result);
    }

    public static <T> ResponseEntity<ApiResult<T>> created(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setData(data);
        result.setMeta(ApiResultMeta.success("Resource created"));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    public static ResponseEntity<ApiResult<Void>> ok() {
        ApiResult<Void> result = new ApiResult<>();
        result.setMeta(ApiResultMeta.success());
        return ResponseEntity.ok(result);
    }

    public static <T> ResponseEntity<ApiResult<T>> error(HttpStatus status, String message, ErrorCode code) {
        ApiResult<T> result = new ApiResult<>();
        result.setMeta(ApiResultMeta.failure(message, code));
        return ResponseEntity.status(status).body(result);
    }
}
```

### Meta Object

```java

@Data
public class ApiResultMeta {
    private boolean success;
    private String message;
    private ErrorCode errorCode;
    private Instant timestamp;

    public static ApiResultMeta success() {
        ApiResultMeta meta = new ApiResultMeta();
        meta.setSuccess(true);
        meta.setTimestamp(Instant.now());
        return meta;
    }

    public static ApiResultMeta failure(String message, ErrorCode errorCode) {
        ApiResultMeta meta = new ApiResultMeta();
        meta.setSuccess(false);
        meta.setMessage(message);
        meta.setErrorCode(errorCode);
        meta.setTimestamp(Instant.now());
        return meta;
    }
}
```

### JSON Shape

```json
{
  "data": {
    ...
  },
  "meta": {
    "success": true,
    "message": null,
    "errorCode": null,
    "timestamp": "2026-02-15T12:00:00Z"
  },
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

---

## 11. Exception Hierarchy

### Base Exception

```java

@Getter
public abstract class ApiOperationException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatus status;

    protected ApiOperationException(String message, ErrorCode errorCode, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    protected ApiOperationException(String message, ErrorCode errorCode) {
        this(message, errorCode, HttpStatus.BAD_REQUEST);
    }
}
```

### Concrete Exceptions

```java
public class ResourceNotFoundException extends ApiOperationException {
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found with id: " + id,
                ErrorCode.RESOURCE_NOT_FOUND,
                HttpStatus.NOT_FOUND);
    }
}

public class DuplicateResourceException extends ApiOperationException {
    public DuplicateResourceException(String resource, String field, Object value) {
        super(resource + " already exists with " + field + ": " + value,
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                HttpStatus.CONFLICT);
    }
}

public class ValidationException extends ApiOperationException {
    public ValidationException(String message) {
        super(message, ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
    }
}

public class InvalidOperationStateException extends ApiOperationException {
    public InvalidOperationStateException(String message) {
        super(message, ErrorCode.INVALID_OPERATION_STATE, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
```

### Error Codes (Enum)

```java
public enum ErrorCode {
    RESOURCE_NOT_FOUND,
    RESOURCE_ALREADY_EXISTS,
    VALIDATION_ERROR,
    INVALID_OPERATION_STATE,
    UNAUTHORIZED,
    FORBIDDEN,
    TOKEN_EXPIRED,
    INTERNAL_ERROR
}
```

### Global Exception Handler

```java

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handle(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ApiResult.error(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(ApiOperationException.class)
    public ResponseEntity<ApiResult<Void>> handle(ApiOperationException ex) {
        log.error("API operation error: {}", ex.getMessage(), ex);
        return ApiResult.error(ex.getStatus(), ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<Void>> handle(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: {}", ex.getMessage());
        return ApiResult.error(HttpStatus.CONFLICT, "Resource conflict", ErrorCode.RESOURCE_ALREADY_EXISTS);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                ApiResult.validationError(errors));
    }
}
```

### Rules

- Every domain exception extends `ApiOperationException`.
- Every exception carries an `ErrorCode` and an `HttpStatus`.
- `GlobalExceptionHandler` is the **only** place that converts exceptions to HTTP responses.
- Never catch and swallow exceptions in use cases — let them propagate.
- Log at `warn` for client errors (4xx), `error` for server errors (5xx).

---

## 12. Validation

### Jakarta Bean Validation on DTOs

```java
public class CreateProductRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotNull
    @Positive
    private BigDecimal price;

    @NotNull
    @PositiveOrZero
    private Integer stock;
}
```

### Custom Validators

```java
// Annotation
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {
    String message() default "Invalid phone number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

// Validator
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {
    private static final Pattern PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;  // @NotNull handles nulls
        return PATTERN.matcher(value).matches();
    }
}
```

### Rules

- Validation annotations go on **request DTOs**, never on entities.
- `@Valid` goes on the controller implementation parameter (alongside `@RequestBody`).
- Custom validators live in `core/validator/`.
- Business rule validation (state checks, cross-field checks) goes in the **use case**, not the validator.

---

## 13. Route Constants

Centralize all API paths in a constants class.

```java
public final class Routes {

    private Routes() {
    }

    public static final String API_PREFIX = "/api";

    public static final class V1 {
        private static final String BASE = API_PREFIX + "/v1";

        public static final class Admin {
            public static final class Product {
                public static final String BASE = V1.BASE + "/admin/products";
                public static final String BY_ID = "/{id}";
            }

            public static final class Category {
                public static final String BASE = V1.BASE + "/admin/categories";
                public static final String BY_ID = "/{id}";
            }

            public static final class Order {
                public static final String BASE = V1.BASE + "/admin/orders";
                public static final String BY_ID = "/{id}";
                public static final String STATUS = "/{id}/status";
            }
        }

        public static final class Public {
            public static final class Product {
                public static final String BASE = V1.BASE + "/products";
                public static final String BY_SLUG = "/{slug}";
            }

            public static final class Category {
                public static final String BASE = V1.BASE + "/categories";
            }

            public static final class Order {
                public static final String BASE = V1.BASE + "/orders";
                public static final String TRACK = "/track";
            }
        }
    }
}
```

### Rules

- All routes are compile-time constants — no magic strings in controllers.
- Nested class structure mirrors the URL hierarchy.
- Separate `Admin` and `Public` namespaces for access-level clarity.

---

## 14. JPA Specifications (Filtering)

Use Spring Data `Specification<T>` for dynamic query building.

```java

@Data
@AllArgsConstructor
public class ProductSpec implements Specification<Product> {
    private String search;
    private Long categoryId;
    private ProductStatus status;

    @Override
    public Predicate toPredicate(Root<Product> root, CriteriaQuery<?> query,
                                 CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        predicates.add(cb.equal(root.get("deleted"), false));

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }

        if (categoryId != null) {
            predicates.add(cb.equal(root.get("category").get("id"), categoryId));
        }

        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
```

### Rules

- Spec classes live in `feature/{name}/spec/`.
- Always include `deleted = false` predicate for soft-deletable entities.
- Pass spec objects from controllers to use cases — never build queries in controllers.

---

## 15. Pagination

### Pagination Request DTO

```java

@Data
@AllArgsConstructor
public class PaginationRequest {
    private int page;
    private int size;
    private String sortBy;
    private Sort.Direction order;
    private boolean paginate;

    public Pageable toPageable() {
        if (!paginate) {
            return Pageable.unpaged();
        }
        return PageRequest.of(page, size, Sort.by(order, sortBy));
    }
}
```

### Pagination Info (in API Response)

```java

@Data
public class PaginationInfo {
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static PaginationInfo from(Page<?> page) {
        PaginationInfo info = new PaginationInfo();
        info.setPage(page.getNumber());
        info.setSize(page.getSize());
        info.setTotalElements(page.getTotalElements());
        info.setTotalPages(page.getTotalPages());
        return info;
    }
}
```

### Usage in Use Case

```java

@Override
public Page<ProductSummaryDto> execute(PaginationRequest pagination, ProductSpec spec) {
    Page<Product> page = productRepository.findAll(spec, pagination.toPageable());
    return page.map(mapper::toSummaryDto);
}
```

---

## 16. Domain Events

Use Spring's `ApplicationEventPublisher` for decoupled cross-feature communication.

### Event Definition

```java

@Getter
@AllArgsConstructor
public class OrderPlacedEvent {
    private final Long orderId;
    private final String customerPhone;
    private final BigDecimal totalAmount;
}
```

### Publishing (in Use Case)

```java

@UseCase
@RequiredArgsConstructor
public class PlaceOrderUseCaseImpl implements PlaceOrderUseCase {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrderDto execute(PlaceOrderRequest request) {
        Order order = buildOrder(request);
        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderPlacedEvent(
                saved.getId(), saved.getCustomerPhone(), saved.getTotal()));

        return mapper.toDto(saved);
    }
}
```

### Consuming

```java

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedEventConsumer {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("asyncEventExecutor")
    public void handle(OrderPlacedEvent event) {
        log.info("Order placed: {}", event.getOrderId());
        notificationService.sendOrderConfirmation(event.getCustomerPhone(), event.getOrderId());
    }
}
```

### Rules

- Publish events **after** the core state change, inside the same transaction.
- Consume events with `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- Make consumers `@Async` to avoid blocking the main transaction.
- Events are immutable value objects — `@Getter @AllArgsConstructor`, no setters.

---

## 17. Security & Authorization

### JWT Filter

```java

@Component
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && tokenService.isValid(token)) {
            Long userId = tokenService.extractUserId(token);
            UserDetails user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

### Method-Level Authorization

```java
// On controller implementation methods:
@PreAuthorize("hasAuthority('PRODUCT_CREATE')")
public ResponseEntity<ApiResult<ProductDto>> create(...) { ...}
```

### Public Endpoints (No Auth)

```java
// In SecurityConfig:
.requestMatchers(Routes.V1.Public.Product.BASE +"/**").

permitAll()
.

requestMatchers(Routes.V1.Public.Category.BASE +"/**").

permitAll()
.

requestMatchers(Routes.V1.Public.Order.TRACK).

permitAll()
```

---

## 18. Auditing

Use **Spring Data JPA JpaAuditing** — never write SQL triggers for `createdAt`/`updatedAt`.

### Configuration

```java

@EnableJpaAuditing
@Configuration
public class JpaAuditingConfig {
}
```

### On Entities

```java

@MappedSuperclass
@Getter
@Setter
public abstract class Auditable {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

Hibernate's `@CreationTimestamp` and `@UpdateTimestamp` handle population automatically.

---

## 19. Configuration

### Location

All `@Configuration` classes live in `application/config/`.

### Standard Configurations

| Config              | Purpose                                                 |
|---------------------|---------------------------------------------------------|
| `JpaAuditingConfig` | Enables `@CreationTimestamp` / `@UpdateTimestamp`       |
| `SecurityConfig`    | HTTP security, CORS, CSRF, session policy, filter chain |
| `AsyncConfig`       | Thread pool for async event processing                  |
| `SwaggerConfig`     | OpenAPI / Swagger documentation                         |
| `StorageConfig`     | File storage adapter setup                              |
| `CacheConfig`       | Redis or in-memory cache                                |

### Environment Profiles

Use Spring profiles for environment separation:

```yaml
# application.yml (shared)
spring:
  profiles:
    active: ${SPRING_PROFILE:dev}

# application-dev.yml
# application-staging.yml
# application-prod.yml
```

---

## 20. Custom Annotations

### @UseCase

```java

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface UseCase {
}
```

Replaces `@Service` for use case classes. Semantically clear — this is an application-level business operation, not a
generic service.

### @EventConsumer (optional)

```java

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface EventConsumer {
}
```

Marks domain event listener classes.

---

## 21. Dependency Rules

### Allowed Dependencies

```
Controller  →  UseCase interface     ✅
Controller  →  Request/Response DTO  ✅
UseCase     →  Repository interface  ✅
UseCase     →  Mapper                ✅
UseCase     →  Other UseCase iface   ✅  (cross-feature)
UseCase     →  Domain Event          ✅
UseCase     →  Exception classes     ✅
Mapper      →  Entity                ✅
Mapper      →  DTO                   ✅
Repository  →  Entity                ✅
```

### Forbidden Dependencies

```
Controller  →  Repository           ❌  (skip the use case layer)
Controller  →  Entity               ❌  (entities never leave the domain)
UseCase     →  Controller           ❌  (reverse direction)
UseCase     →  Other feature's Repo ❌  (use that feature's use case instead)
Entity      →  DTO                  ❌  (entity is unaware of presentation)
DTO         →  Entity               ❌  (DTO is unaware of domain)
Core        →  Feature              ❌  (core is independent)
```

---

## 22. Naming Conventions Summary

| Element              | Pattern                            | Example                        |
|----------------------|------------------------------------|--------------------------------|
| Feature package      | `feature.{name}`                   | `feature.product`              |
| Entity               | PascalCase singular                | `Product`                      |
| Repository           | `{Entity}Repository`               | `ProductRepository`            |
| UseCase interface    | `{Verb}{Entity}UseCase`            | `CreateProductUseCase`         |
| UseCase impl         | `{Verb}{Entity}UseCaseImpl`        | `CreateProductUseCaseImpl`     |
| Controller interface | `{Audience}{Entity}Controller`     | `AdminProductController`       |
| Controller impl      | `{Audience}{Entity}ControllerImpl` | `AdminProductControllerImpl`   |
| Request DTO          | `{Verb}{Entity}Request`            | `CreateProductRequest`         |
| Response DTO         | `{Entity}Dto`                      | `ProductDto`                   |
| Summary DTO          | `{Entity}SummaryDto`               | `ProductSummaryDto`            |
| Mapper               | `{Entity}Mapper`                   | `ProductMapper`                |
| Spec                 | `{Entity}Spec`                     | `ProductSpec`                  |
| Domain event         | `{Entity}{Verb}Event` (past tense) | `OrderPlacedEvent`             |
| Event consumer       | `{Event}Consumer`                  | `OrderPlacedEventConsumer`     |
| Enum                 | PascalCase                         | `ProductStatus`, `OrderStatus` |
| Route constant       | `Routes.V1.{Audience}.{Entity}`    | `Routes.V1.Admin.Product.BASE` |

---

*This document defines the architecture. All Spring Boot backend projects must follow these patterns unless a specific
deviation is documented and justified.*
