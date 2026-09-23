# Global Code Generation Rules

- Follow Hexagonal Architecture (ports & adapters)
- Use constructor injection, avoid field injection
- Write idiomatic Kotlin: data classes, immutability, null-safety
- Every generated class must have unit tests
- Output files using fenced code blocks with the target path:
  ```language:path/relative/to/workspace/File.ext
  <content>
  ```
- Use `MODIFY:path` (optionally `:start-end`) to change an existing file
  and `DELETE:path` to remove one
