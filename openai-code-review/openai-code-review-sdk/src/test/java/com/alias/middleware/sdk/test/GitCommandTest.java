package com.alias.middleware.sdk.test;

import com.alias.middleware.sdk.config.AppConfig;
import com.alias.middleware.sdk.infrastructure.git.GitCommand;
import org.junit.Test;

public class GitCommandTest {

    @Test
    public void test_getPrDiffs() throws Exception {
        AppConfig cfg = AppConfig.getInstance();
        String url = "https://github.com/AliasJeff/alias-rag-review/pull/3";

        GitCommand gitCommand = new GitCommand(cfg.getString("github", "token"));
        String prDiff = gitCommand.getPrDiff(url);

        System.out.println("prDiff" + prDiff);
    }
}
