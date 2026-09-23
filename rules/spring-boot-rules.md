# Spring Boot Rules

- Controllers: `@RestController` + `@RequestMapping`, constructor injection
- Services: `@Service`, transactional boundaries with `@Transactional`
- Persistence: Spring Data repositories, no direct EntityManager in controllers
- Validation: Jakarta Bean Validation (`@Valid`, `@NotNull`, ...)
- Errors: `@RestControllerAdvice` with structured error responses
- Tests: `@SpringBootTest` or sliced tests (`@WebMvcTest`, `@DataJpaTest`)
