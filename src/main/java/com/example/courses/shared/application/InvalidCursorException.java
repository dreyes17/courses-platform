package com.example.courses.shared.application;

/** A pagination cursor that this API didn't issue, or that was altered. */
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException() {
        super("The cursor is not valid; use the nextCursor value returned by the previous page");
    }
}
