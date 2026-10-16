package com.github.dewarepk.synthetixlib.config.codec;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Thrown when data cannot be decoded. Carries <b>all</b> problems found, not only the first.
 *
 * <pre>{@code
 * try {
 *     codecs.decode(tree, AuctionConfig.class);
 * } catch (DecodeException e) {
 *     e.problems().forEach(p -> logger.warning(p.toString()));   // "zones.spawn.radius: must be >= 1 (was 0)"
 * }
 * }</pre>
 */
public final class DecodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<Problem> problems;

    /** Single problem. */
    public DecodeException(final String path, final String message) {
        this(List.of(new Problem(path, message)));
    }

    /**
     * @param problems at least one problem
     * @throws IllegalArgumentException if {@code problems} is empty
     */
    public DecodeException(final List<Problem> problems) {
        super(summary(problems));
        this.problems = List.copyOf(problems);
    }

    /** @return every problem found, in document order; never empty */
    public List<Problem> problems() {
        return problems;
    }

    private static String summary(final List<Problem> problems) {
        Objects.requireNonNull(problems, "problems");
        if (problems.isEmpty()) {
            throw new IllegalArgumentException("problems must not be empty");
        }
        if (problems.size() == 1) {
            return problems.get(0).toString();
        }
        return problems.size() + " problems:\n" + problems.stream()
                .map(p -> "  - " + p)
                .collect(Collectors.joining("\n"));
    }
}
