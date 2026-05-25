package demo.editorial.application;

import java.util.List;

public record Article(String title, String body, List<String> keyPoints) {}
