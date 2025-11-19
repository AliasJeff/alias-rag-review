-- ===========================================================
-- PostgreSQL 初始化脚本（AI Code Review 无登录版）
-- ===========================================================

-- 必要扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

CREATE EXTENSION IF NOT EXISTS "vector";

CREATE TABLE IF NOT EXISTS vector_store (
    id uuid PRIMARY KEY,
    content text,
    metadata jsonb,
    embedding vector(1024)
);

-- ===========================================================
-- 客户端用户表（替代 user 表）
-- 使用浏览器 localStorage 生成的 UUID 标识不同用户
-- ===========================================================
CREATE TABLE IF NOT EXISTS client_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_identifier UUID NOT NULL UNIQUE,   -- 前端 localStorage 生成
    github_token TEXT,                        -- 加密后的 GitHub Token
    openai_api_key TEXT,                      -- 加密后的 OpenAI Key
    encrypted BOOLEAN DEFAULT TRUE,           -- 标记 token 是否已加密
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ===========================================================
-- 对话表（与 client_identifier 关联）
-- 一个浏览器用户只能访问自己的 conversation
-- ===========================================================
CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_identifier UUID NOT NULL REFERENCES client_users(client_identifier) ON DELETE CASCADE,
    title VARCHAR(255),
    pr_url TEXT,
    repo VARCHAR(255),
    pr_number INTEGER,
    status VARCHAR(50) DEFAULT 'active', -- active/closed/archived/error
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ===========================================================
-- 消息表
-- ===========================================================
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,        -- user/assistant/system
    type VARCHAR(50) DEFAULT 'text',  -- text/code/analysis
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ===========================================================
-- PR 文件快照表（缓存 diff / 内容）
-- ===========================================================
CREATE TABLE IF NOT EXISTS pr_snapshots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    url TEXT NOT NULL,
    client_identifier VARCHAR(255),
    repo_name VARCHAR(255),
    pr_number INT,
    branch VARCHAR(255),

    file_changes JSONB DEFAULT '{}'::jsonb,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ===========================================================
-- 索引
-- ===========================================================
CREATE INDEX IF NOT EXISTS idx_conversations_client_id
    ON conversations(client_identifier);

CREATE INDEX IF NOT EXISTS idx_conversations_pr_url
    ON conversations USING gin (pr_url gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_id
    ON messages(conversation_id);

CREATE INDEX IF NOT EXISTS idx_pr_snapshots_conversation_id
    ON pr_snapshots(conversation_id);

CREATE INDEX IF NOT EXISTS idx_pr_snapshots_file_path
    ON pr_snapshots USING gin (file_path gin_trgm_ops);


-- ===========================================================
-- updated_at 自动更新时间戳触发器
-- ===========================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_client_users_updated_at
    BEFORE UPDATE ON client_users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_conversations_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


-- ===========================================================
-- 视图：对话 + 最新消息（用于会话列表展示）
-- ===========================================================
CREATE OR REPLACE VIEW client_conversations_with_latest_message AS
SELECT
    c.id,
    c.client_identifier,
    c.title,
    c.pr_url,
    c.repo,
    c.pr_number,
    c.status,
    c.created_at,
    c.updated_at,

    m.content AS latest_message,
    m.created_at AS latest_message_time

FROM conversations c
         LEFT JOIN LATERAL (
    SELECT content, created_at
    FROM messages
    WHERE conversation_id = c.id
    ORDER BY created_at DESC
        LIMIT 1
) m ON true;
