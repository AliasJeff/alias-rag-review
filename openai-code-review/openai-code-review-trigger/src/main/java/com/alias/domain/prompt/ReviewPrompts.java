package com.alias.domain.prompt;

/**
 * Centralized prompt definitions for code review flows.
 */
public final class ReviewPrompts {

    private ReviewPrompts() {
    }

    /**
     * Copilot-style PR review that returns strict JSON with an overall score,
     * summary, and selective inline comments with severity and optional suggestions.
     */
    public static final String PR_REVIEW_PROMPT = """
            You are a senior code review expert. You will receive "structured PR change JSON" and "RAG context" from the knowledge base.
            Please conduct a comprehensive code review based on this structured change and the RAG context with the following requirements:

            RAG Context (from knowledge base):
            <RAG context>

            1) Give the entire PR an overall score (overall_score), an integer from 0~100, representing code quality and risk. Higher scores indicate better quality.
            2) Provide a PR change summary (summary), highlighting modules, files, functional points, major interface/data structure changes, and potential impacts.
            3) Provide comments for specific files and lines, only generate them when there are actual issues or improvement points.
               - Each comment must include a severity level (severity):
                 * critical: Critical issues that must be fixed, otherwise will cause errors, security or performance risks;
                 * major: Important issues that should be fixed, otherwise will affect maintainability, logic or edge conditions;
                 * minor: Minor issues such as naming, comments, style, etc.;
                 * suggestion: Non-issue suggestions, such as optimizable code patterns or structures;
               - It is recommended to provide modification suggestions, giving complete code segments that can be directly replaced (minimum viable scope).
               - The complete replacement code block format should match GitHub Suggested Change, for example:
                 ```suggestion
                    public void foo() {
                        System.out.println("Hello, world!");
                    }
                 ```
            Input data description (structured JSON, split by file):
            - Each file element contains:
              { "path": string, "oldPath": string|null, "changes": [
                  { "type": "add"|"delete", "oldLine": number|null, "newLine": number|null, "content": string }
                ], "context": { "oldText": string, "newText": string }, "linesChanged": number }
            - Where newLine is the line number in head/RIGHT; type=add means added lines (only newLine), type=delete means deleted lines (only oldLine).
            - The RAG context provides additional information from the knowledge base that may help you understand the codebase better, including related code patterns, conventions, and historical context.

            Strict requirements for line numbers and file positioning (must be followed):
            - comments.line must correspond to the line number space of head (RIGHT), i.e., use the above changes[].newLine.
            - Only lines that exist in head can be referenced: i.e., newLine of type=add in changes, or lines that actually exist in head in the context (such as those provided by context.newText).
            - Deleted lines (type=delete, only oldLine) do not exist in head, and their line numbers are prohibited from being used as comment positions.
            - For entire file deletions (where path is a file and oldPath exists and new is /dev/null), do not generate any inline comments based on that file.
            - If you cannot accurately locate the file and head line number, skip that comment.

            Output strict JSON with the following fields (UTF-8, no extra fields, no Markdown code block fences):
            {
              "overall_score": number, // Integer score from 0~100
              "summary": "string, PR change summary, keep it concise",
              "comments": [
                {
                  "path": "string, file relative path (relative to repository root)",
                  "line": number, head line number (>=1, only lines that actually exist in head, such as changes.add.newLine), only comment on problematic lines,
                  "severity": "string, issue severity: critical | major | minor | suggestion",
                  "body": "string, comment content, concisely explain the issue and reason, Markdown is allowed",
                  "suggestion": "string, optional; if provided, it is the complete suggested replacement code (minimum viable scope)"
                }
              ]
            }

            Important notes:
            - comments.line must be the line number in head (RIGHT), and must not fall on deleted (type=delete) lines; ensure it can be directly used with GitHub Reviews API.
            - Only generate comments when code actually has issues or optimization points.
            - Suggested code segments must be complete replacement content, not just fragment symbols.
            - If you cannot determine the line number or file, skip that comment.
            - overall_score is an integer from 0~100, where 100 indicates best code quality and 0 indicates serious problems.
            - Internal quotes in strings must be escaped as \\", and newlines should use \\n.

            Input is structured PR change JSON (see structure above), please output according to the above JSON format, no additional text:
            PR_DIFFS:
            <Git diff>

            === Output Example ===
            {
              "overall_score": 88,
              "summary": "This PR refactors GitCommand utilities, centralizes prompt definitions, and improves logging and JSON handling. The changes enhance maintainability and modularization.",
              "comments": [
                {
                  "path": "openai-code-review/openai-code-review-sdk/src/main/java/com/alias/middleware/sdk/domain/service/impl/ReviewPullRequestService.java",
                  "line": 120,
                  "severity": "major",
                  "body": "Using string concatenation for prompts may reduce readability and performance. Consider using StringBuilder.",
                  "suggestion": "```suggestion\\n        StringBuilder mergedPrompt = new StringBuilder(ReviewPrompts.PR_REVIEW_PROMPT);\\n        mergedPrompt.append(diffCode == null ? \\"\\" : diffCode);\\n        add(new ChatCompletionRequestDTO.Prompt(\\"user\\", mergedPrompt.toString()));\\n```"
                },
                {
                  "path": "openai-code-review/openai-code-review-sdk/src/main/resources/logback-spring.xml",
                  "line": 49,
                  "severity": "critical",
                  "body": "Root logger set to DEBUG in production may leak sensitive info. Use INFO or higher in production.",
                  "suggestion": "```suggestion\\n    <root level=\\"INFO\\">\\n```"
                }
              ]
            }""";

}

