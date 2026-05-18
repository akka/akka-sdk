package demo.editorial.application;

import java.util.List;

public record ArticleDraft(String title, String body, List<String> documentIds) {}
