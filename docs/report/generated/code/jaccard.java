// com/iitsm/strategy/JaccardSimilarityStrategy.java
public double similarity(String a, String b) {
    Set<String> first = TextTokenizer.tokens(a);
    Set<String> second = TextTokenizer.tokens(b);

    Set<String> union = new HashSet<>(first);
    union.addAll(second);
    if (union.isEmpty()) {
        return 0.0;
    }
    Set<String> shared = new HashSet<>(first);
    shared.retainAll(second);
    return (double) shared.size() / union.size();
}
