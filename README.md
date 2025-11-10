## alias-rag-review

Unified multi-module Maven repository containing:
- ai-rag-knowledge: RAG system (modules: rag-dev-tech-api, rag-dev-tech-app, rag-dev-tech-trigger)
- openai-code-review: Code review SDK and demo (modules: openai-code-review-sdk, openai-code-review-test)

### Build
- Build all modules at repo root:
  - `mvn -T 1C -DskipTests install`
- Build specific submodule:
  - `cd ai-rag-knowledge && mvn -DskipTests install`
  - `cd openai-code-review && mvn -DskipTests install`

### Structure
alias-rag-review/
- pom.xml
- ai-rag-knowledge/
  - pom.xml
  - rag-dev-tech-api/
  - rag-dev-tech-app/
  - rag-dev-tech-trigger/
- openai-code-review/
  - pom.xml
  - openai-code-review-sdk/
  - openai-code-review-test/

### License
Apache License 2.0, see LICENSE file.


