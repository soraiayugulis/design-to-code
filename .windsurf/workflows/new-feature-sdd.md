---
auto_execution_mode: 2
---

[Workflow: Spec-Driven-New-Feature]

Use this workflow when you need to implement a new feature based on a technical specification.

1. Read the technical specification Markdown file indicated by the user.
2. Identify and map the API contracts, exception rules, and entities involved.
3. Use @skill:tdd-expert and @skill:kotlin-spring-dev to implement the feature following strictly the TDD process.
4. Follow the spec document structure and content strictly.
5. If the repository is empty, use the @workflow:new-repo else use the @workflow:new-fix.
6. Write the unit test suite based strictly on the error and success scenarios from the Spec.
7. Develop the adapters (Controllers and Repositories) and ensure all tests pass.
8. In case there is no session describing the implementation phases create a new doc in docs/spec/ detailing the implementation phases.
9. Each phase must be in a separate feature branch.
10. Each branch must have granular commits. Use @workflow:pre-commit
11. Then use @workflow:pepare-pr
12. Use @global_rules