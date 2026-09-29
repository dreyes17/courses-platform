package com.example.courses.shared.application;

import java.util.List;

/**
 * One page of a keyset-paginated listing. {@code nextCursor} is opaque: send it back as {@code cursor} to get
 * the following page; it is null on the last page.
 */
public record CursorPage<T>(List<T> content, String nextCursor) {
}
