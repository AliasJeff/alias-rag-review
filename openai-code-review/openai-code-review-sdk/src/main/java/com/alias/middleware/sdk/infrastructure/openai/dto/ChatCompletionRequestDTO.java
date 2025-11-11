package com.alias.middleware.sdk.infrastructure.openai.dto;

import com.alias.middleware.sdk.domain.model.ModelEnum;

import java.util.List;

public class ChatCompletionRequestDTO {

    private String model = ModelEnum.GLM_4_FLASH.getCode();
    private List<Prompt> messages;

    public static class Prompt {
        private String role;
        private String content;

        public Prompt() {
        }

        public Prompt(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        @Override
        public String toString() {
            return "Prompt{" +
                    "role='" + role + '\'' +
                    ", content='" + content + '\'' +
                    '}';
        }

    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Prompt> getMessages() {
        return messages;
    }

    public void setMessages(List<Prompt> messages) {
        this.messages = messages;
    }

    @Override
    public String toString() {
        return "ChatCompletionRequestDTO{" +
                "model='" + model + '\'' +
                ", messages=" + messages +
                '}';
    }
}
